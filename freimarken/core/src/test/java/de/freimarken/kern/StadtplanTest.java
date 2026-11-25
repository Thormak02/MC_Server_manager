package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Geometrie Altfurts - Markt, Gassen, geschlossene Zeilen, Mauer.
 *
 * <p>Die Erwartungen sind die Zahlen der Dokumentation und NICHT aus
 * {@link Stadtplan} geholt: Eine Probe, die ihren Ring von {@code zeileInnenVon}
 * erfragt, haelt nur noch, dass eine Zahl sich selbst gleicht.
 *
 * <p>Gemessen wird an einer Stelle NEBEN den Achsen ({@code MITTE_Z + 8}): Auf der Achse
 * liegt die Radialgasse, und die gewinnt vor jedem Band - sonst pruefte die Bandtafel
 * ueberall dasselbe Wort.
 */
@DisplayName("Die Geometrie Altfurts")
class StadtplanTest {

    /** Der Abstand von der Mittelachse, auf dem keine Radialgasse liegt. */
    private static final int NEBEN_DER_ACHSE = 8;

    /** Die Lage auf Ring {@code ring} an der Laengsstelle {@code laengs} der Ostseite. */
    private static Stadtplan.Lage aufRing(Stadtplan plan, int ring, int laengs) {
        return plan.lageBei(Stadtplan.MITTE_X + ring, laengs);
    }

    /**
     * Eine Laengsstelle, an der DIESES Band nicht ausgebogen ist.
     *
     * <p>Dort steht die Zeile genau so, wie die Tafel im Dateikopf sie zeichnet.
     *
     * <p><b>Je Band und nicht fuer alle</b> [24.11.2026]: Der erste Entwurf suchte eine
     * Stelle, an der ALLE vier Zeilen gerade sind. Die gibt es womoeglich nicht: Alle
     * Baender haben dieselbe Wellenlaenge und nur verschiedene Phasen, also wiederholt
     * sich das Bild nach {@link Stadtplan#KRUMM_WELLE} Bloecken - was in einer
     * Wellenlaenge nicht zusammenfaellt, faellt nie zusammen. Jeder Ring gehoert aber
     * genau einem Band, und darum genuegt eine Stelle je Band.
     *
     * <p><b>Gefragt wird die GASSE und nicht die Welle</b>: Die Gassenkante ist die
     * Hausfront, und ein Haus nimmt den Ausschlag an seiner Mitte - an einer Stelle, an
     * der die Welle Null ist, kann die Kante darum trotzdem eingezogen sein. Gesucht ist
     * also die Stelle, an der die Zeile WIRKLICH am Bandrand anfaengt.
     *
     * <p><b>Abgeschritten wird nur die ZEILE</b>: Jenseits ihrer
     * {@link Stadtplan#zeilenreichweite} liegt der Eckplatz, und dort ist jede Antwort
     * eine andere Frage.
     *
     * @param kantenRing der Ring, den der Bogen in diesem Band frisst: bei der inneren
     *                   Zeile der innerste, bei der aeusseren der aeusserste
     */
    private static int ohneBogen(Stadtplan plan, int kantenRing) {
        for (int laengs = Stadtplan.MITTE_Z + NEBEN_DER_ACHSE;
                laengs <= Stadtplan.MITTE_Z + Stadtplan.zeilenreichweite(kantenRing);
                laengs++) {
            if (aufRing(plan, kantenRing, laengs) == Stadtplan.Lage.ZEILE) {
                return laengs;
            }
        }
        throw new AssertionError("Ring " + kantenRing + " gehoert in der ganzen Zeile"
                + " nirgends zur Zeile - dort faengt die Zeile nie am Bandrand an");
    }

    /**
     * Die Laengsstelle, an der dieser Ring so liegt, wie die Tafel ihn zeichnet.
     *
     * <p>Fuer einen Ring in einem Zeilenband ist das eine Stelle ohne Bogen; fuer alle
     * anderen Ringe ist die Stelle gleichgueltig, denn der Bogen frisst nur
     * Zeilenbloecke.
     */
    private static int tafelstelle(Stadtplan plan, int ring) {
        for (int q = 0; q < plan.quartiere(); q++) {
            // DIE INNERE ZEILE hat ihre Gasse innen, die AEUSSERE aussen - der Bogen
            // frisst darum je Band den Ring an der jeweils eigenen Kante.
            int innenVon = plan.zeileInnenVon(q);
            if (ring >= innenVon && ring < innenVon + Stadtplan.ZEILE_TIEFE) {
                return ohneBogen(plan, innenVon);
            }
            int aussenVon = plan.zeileAussenVon(q);
            if (ring >= aussenVon && ring < aussenVon + Stadtplan.ZEILE_TIEFE) {
                return ohneBogen(plan, aussenVon + Stadtplan.ZEILE_TIEFE - 1);
            }
        }
        return Stadtplan.MITTE_Z + NEBEN_DER_ACHSE;
    }

    @Test
    @DisplayName("Die Baender liegen hintereinander und lassen keine Luecke")
    void dieBaender() {
        Stadtplan plan = Stadtplan.altfurt();

        // Die Tafel der Dokumentation, Ring fuer Ring abgeschritten.
        Map<Integer, Stadtplan.Lage> tafel = new TreeMap<>();
        // Die Plaetze: Auf z = MITTE_Z + 8 liegt der Marktplatz selbst.
        for (int r = NEBEN_DER_ACHSE; r <= 40; r++) {
            tafel.put(r, Stadtplan.Lage.MARKT);
        }
        for (int r = 41; r <= 51; r++) {
            tafel.put(r, Stadtplan.Lage.KERN);
        }
        for (int r = 52; r <= 56; r++) {
            tafel.put(r, Stadtplan.Lage.RINGGASSE);
        }
        for (int r = 57; r <= 78; r++) {
            tafel.put(r, Stadtplan.Lage.ZEILE);
        }
        for (int r = 79; r <= 83; r++) {
            tafel.put(r, Stadtplan.Lage.RINGGASSE);
        }
        for (int r = 84; r <= 105; r++) {
            tafel.put(r, Stadtplan.Lage.ZEILE);
        }
        for (int r = 106; r <= 110; r++) {
            tafel.put(r, Stadtplan.Lage.RINGGASSE);
        }
        for (int r = 111; r <= 113; r++) {
            tafel.put(r, Stadtplan.Lage.MAUER);
        }
        for (int r = 114; r <= 121; r++) {
            tafel.put(r, Stadtplan.Lage.AUSSEN);
        }

        // ABGESCHRITTEN AN EINER STELLE OHNE BOGEN [24.11.2026]: Seit die Gassen
        // ausbiegen, haengt die Grenze zwischen Gasse und Zeile an der Laengsstelle.
        // Diese Tafel ist die der GERADEN Stadt - sie haelt die Baender selbst fest.
        // Dass der Bogen wirklich ausschlaegt, prueft "Die Gassen biegen aus".
        for (Map.Entry<Integer, Stadtplan.Lage> zeile : tafel.entrySet()) {
            int gerade = tafelstelle(plan, zeile.getKey());
            Stadtplan.Lage gemessen = aufRing(plan, zeile.getKey(), gerade);
            // EINE GASSE HAT SEIT DEM 24.11.2026 EINE KANTE: Ihr erster und ihr letzter
            // Ring sind BORDSTEIN. Die Tafel nennt das Band, nicht die Kante - sie
            // nimmt darum beides als Gasse, und dass die Kante wirklich liegt, haelt
            // "Jede Gasse hat eine Kante".
            if (zeile.getValue() == Stadtplan.Lage.RINGGASSE) {
                assertTrue(Stadtplan.istWeg(gemessen), "Ring " + zeile.getKey()
                        + " an der Laengsstelle " + gerade + " ist Weg, nicht "
                        + gemessen);
                continue;
            }
            assertEquals(zeile.getValue(), gemessen,
                    "Ring " + zeile.getKey() + " an der Laengsstelle " + gerade);
        }
        // UND DIE ZAHL der gepruefeten Ringe, damit eine geschrumpfte Tafel auffaellt.
        assertEquals(114, tafel.size(), "Ring 8 bis 121 abgeschritten - eine"
                + " geschrumpfte Tafel prueft weniger, ohne rot zu werden");
    }

    @Test
    @DisplayName("Die drei Plaetze liegen hintereinander und fuellen die 81 Bloecke")
    void dieDreiPlaetze() {
        Stadtplan plan = Stadtplan.altfurt();

        // DIE SUMME MUSS AUFGEHEN: Drei Platztiefen und zwei Kanten sind der ganze
        // Platz. Eine Luecke darin waere ein Streifen ohne Lage.
        assertEquals(2 * Stadtplan.MARKT_RING + 1,
                Stadtplan.KIRCHPLATZ_TIEFE + 2 * Stadtplan.PLATZKANTE_BREITE
                        + Stadtplan.MARKT_TIEFE + Stadtplan.RATHAUSPLATZ_TIEFE,
                "die drei Plaetze und die zwei Kanten fuellen den Platz genau");

        // VON NORDEN NACH SUEDEN ABGESCHRITTEN, aber NEBEN der Mittelachse
        // [24.11.2026]: Auf der Achse liegt seit heute der KREUZARM - der Weg, der von
        // der Marktmitte zu den vier Toren fuehrt. Er durchschneidet die drei Plaetze,
        // und genau das ist seine Aufgabe. Die Tiefen der Plaetze misst man darum ein
        // Stueck daneben; dass der Arm wirklich durchschneidet, haelt
        // "Der Kreuzarm fuehrt von der Mitte bis an die Mauer".
        int neben = Stadtplan.MITTE_X + NEBEN_DER_ACHSE;
        assertTrue(NEBEN_DER_ACHSE > Stadtplan.KREUZ_HALB,
                "die Messlinie muss NEBEN dem Arm liegen: " + NEBEN_DER_ACHSE
                        + " gegen " + Stadtplan.KREUZ_HALB);
        // GERECHNET AUS DEN TIEFEN, nicht als absolute z-Werte [24.11.2026]: Bis
        // heute standen hier die Zahlen -27 bis 53, gemessen an einer Mitte bei
        // z 13. Mit der Mitte auf 0/0 waren sie alle falsch - und zwar still, denn
        // eine Zahl sieht nicht falsch aus. Jetzt wandert die Probe mit.
        int ab = Stadtplan.MITTE_Z - Stadtplan.MARKT_RING;
        int kirchplatzBis = ab + Stadtplan.KIRCHPLATZ_TIEFE - 1;
        int ersteKanteAb = kirchplatzBis + 1;
        int ersteKanteBis = ersteKanteAb + Stadtplan.PLATZKANTE_BREITE - 1;
        int marktAb = ersteKanteBis + 1;
        int marktBis = marktAb + Stadtplan.MARKT_TIEFE - 1;
        int zweiteKanteAb = marktBis + 1;
        int zweiteKanteBis = zweiteKanteAb + Stadtplan.PLATZKANTE_BREITE - 1;
        int rathausAb = zweiteKanteBis + 1;
        int rathausBis = rathausAb + Stadtplan.RATHAUSPLATZ_TIEFE - 1;
        assertEquals(Stadtplan.MITTE_Z + Stadtplan.MARKT_RING, rathausBis,
                "die fuenf Streifen fuellen den Platz bis zum Suedrand");

        assertEquals(Stadtplan.Lage.KIRCHPLATZ, plan.lageBei(neben, ab), "Nordrand");
        assertEquals(Stadtplan.Lage.KIRCHPLATZ, plan.lageBei(neben, kirchplatzBis),
                "Kirchplatz bis");
        assertEquals(Stadtplan.Lage.PLATZKANTE, plan.lageBei(neben, ersteKanteAb),
                "Kante ab");
        assertEquals(Stadtplan.Lage.PLATZKANTE, plan.lageBei(neben, ersteKanteBis),
                "Kante bis");
        assertEquals(Stadtplan.Lage.MARKT, plan.lageBei(neben, marktAb), "Markt ab");
        // EIN STUECK NEBEN DER MITTE und nicht auf ihr: Auf der Mittelhoehe liegt der
        // OSTWEST-Arm, und der gilt dort, wie weit man in x auch ausweicht.
        assertEquals(Stadtplan.Lage.MARKT,
                plan.lageBei(neben, Stadtplan.MITTE_Z + NEBEN_DER_ACHSE),
                "mitten auf dem Markt");
        assertEquals(Stadtplan.Lage.MARKT, plan.lageBei(neben, marktBis), "Markt bis");
        assertEquals(Stadtplan.Lage.PLATZKANTE, plan.lageBei(neben, zweiteKanteAb),
                "zweite Kante ab");
        assertEquals(Stadtplan.Lage.PLATZKANTE, plan.lageBei(neben, zweiteKanteBis),
                "bis");
        assertEquals(Stadtplan.Lage.RATHAUSPLATZ, plan.lageBei(neben, rathausAb),
                "Rathausplatz ab");
        assertEquals(Stadtplan.Lage.RATHAUSPLATZ, plan.lageBei(neben, rathausBis),
                "Suedrand");
        // DER ANKUNFTSPUNKT IST DER KREUZUNGSPLATZ: Dort treffen sich alle vier Arme,
        // und wer die Welt betritt, steht auf der Fahrbahn und sieht sie in alle vier
        // Richtungen weglaufen. Genau das hat der Betreiber verlangt.
        assertEquals(Stadtplan.Lage.RADIALGASSE,
                plan.lageBei(Stadtplan.MITTE_X, Stadtplan.MITTE_Z), "die Ankunft");
        // UND AUF DER ACHSE SELBST liegt ueber all diese Tiefen hinweg der Arm.
        for (int z : new int[] {ab, kirchplatzBis, ersteKanteAb, marktAb, marktBis,
                zweiteKanteAb, rathausAb, rathausBis}) {
            assertEquals(Stadtplan.Lage.RADIALGASSE,
                    plan.lageBei(Stadtplan.MITTE_X, z),
                    "auf der Achse bei z " + z + " laeuft die Fahrbahn");
        }

        // UND IN DER BREITE: 81 Bloecke, also MARKT_RING nach jeder Seite. Gemessen
        // auf einer z-Linie NEBEN der Mittelachse, denn auf ihr laeuft der Ostwest-Arm.
        int nebenZ = Stadtplan.MITTE_Z + NEBEN_DER_ACHSE;
        assertEquals(Stadtplan.Lage.MARKT,
                plan.lageBei(Stadtplan.MITTE_X - Stadtplan.MARKT_RING, nebenZ),
                "Westrand des Platzes");
        assertEquals(Stadtplan.Lage.MARKT,
                plan.lageBei(Stadtplan.MITTE_X + Stadtplan.MARKT_RING, nebenZ),
                "Ostrand");
        // DAHINTER DER RAHMEN - gemessen neben dem Arm, denn auf z 13 und auf x 72
        // laeuft er durch den Kern hindurch zum Tor.
        assertEquals(Stadtplan.Lage.KERN,
                plan.lageBei(Stadtplan.MITTE_X - Stadtplan.MARKT_RING - 1, nebenZ),
                "dahinter der Rahmen");
        assertEquals(Stadtplan.Lage.KERN,
                plan.lageBei(Stadtplan.MITTE_X + Stadtplan.MARKT_RING + 1, nebenZ),
                "und gegenueber");
        int imKern = Stadtplan.MITTE_Z + Stadtplan.MARKT_RING + 1;
        assertEquals(Stadtplan.Lage.KERN, plan.lageBei(neben, imKern),
                "neben dem Bundesratshaus");
        assertEquals(Stadtplan.Lage.KERN,
                plan.lageBei(neben, Stadtplan.MITTE_Z - Stadtplan.MARKT_RING - 1),
                "neben der Stadtkirche");
        // UND DURCH DIE BEIDEN HINDURCH der Arm: Kirche und Rathaus ueberspannen ihn,
        // also geht er als LAUBE durch sie - das haelt die Laubenprobe in BauplanTest.
        assertEquals(Stadtplan.Lage.RADIALGASSE,
                plan.lageBei(Stadtplan.MITTE_X, imKern),
                "im Bundesratshaus laeuft die Fahrbahn");
        assertEquals(Stadtplan.Lage.RADIALGASSE,
                plan.lageBei(Stadtplan.MITTE_X,
                        Stadtplan.MITTE_Z - Stadtplan.MARKT_RING - 1),
                "und in der Stadtkirche auch");

        // JEDER PLATZ KOMMT WIRKLICH VOR, und zwar in der Flaeche, die seine Tiefe
        // verspricht - eine Lage, die nur auf der Achse auftaucht, waere ein Streifen.
        Map<Stadtplan.Lage, Integer> flaeche = new EnumMap<>(Stadtplan.Lage.class);
        for (int x = Stadtplan.MITTE_X - Stadtplan.MARKT_RING;
                x <= Stadtplan.MITTE_X + Stadtplan.MARKT_RING; x++) {
            for (int z = Stadtplan.MITTE_Z - Stadtplan.MARKT_RING;
                    z <= Stadtplan.MITTE_Z + Stadtplan.MARKT_RING; z++) {
                flaeche.merge(plan.lageBei(x, z), 1, Integer::sum);
            }
        }
        int breite = 2 * Stadtplan.MARKT_RING + 1;
        // DER KREUZARM SCHNEIDET DURCH [24.11.2026]: Er laeuft in Nord-Sued ueber alle
        // drei Plaetze und beide Kanten, also fehlt jedem Streifen die Armbreite.
        // GERECHNET und nicht neu festgenagelt: Eine Zahl, die ich hier eintrage,
        // stimmt beim naechsten Mal nicht mehr, und dann dreht man sie hoch statt
        // nachzusehen.
        int arm = 2 * Stadtplan.KREUZ_HALB + 1;
        assertEquals((breite - arm) * Stadtplan.KIRCHPLATZ_TIEFE,
                flaeche.get(Stadtplan.Lage.KIRCHPLATZ), "Kirchplatz ohne den Nordarm");
        assertEquals((breite - arm) * Stadtplan.RATHAUSPLATZ_TIEFE,
                flaeche.get(Stadtplan.Lage.RATHAUSPLATZ),
                "Rathausplatz ohne den Suedarm");
        assertEquals((breite - arm) * 2 * Stadtplan.PLATZKANTE_BREITE,
                flaeche.get(Stadtplan.Lage.PLATZKANTE), "die zwei Kanten");
        // DEM MARKT FEHLT MEHR: Durch ihn laeuft AUCH der Ostwest-Arm, und zwar quer
        // ueber seine ganze Breite. Der Kreuzungsplatz in der Mitte gehoert beiden
        // Armen - er darf nur EINMAL abgezogen werden.
        int marktOhneNord = (breite - arm) * Stadtplan.MARKT_TIEFE;
        int ostwestImMarkt = (breite - arm) * arm;
        assertEquals(marktOhneNord - ostwestImMarkt,
                flaeche.get(Stadtplan.Lage.MARKT), "Marktplatz ohne beide Arme");

        // DER ARM SELBST liegt als Fahrbahn und Kante auf dem Platz, und seine Flaeche
        // muss die der Plaetze genau ergaenzen - sonst hat eine Lage einen Streifen
        // verloren, den keine andere bekommen hat.
        int platzflaeche = breite * breite;
        int gezaehlt = flaeche.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(platzflaeche, gezaehlt,
                "jeder Block des Platzes hat genau eine Lage");
        assertTrue(flaeche.get(Stadtplan.Lage.RADIALGASSE) > 0, "die Fahrbahn liegt da");
        assertTrue(flaeche.get(Stadtplan.Lage.BORDSTEIN) > 0, "und ihre Kante auch");
        assertEquals(6, flaeche.size(), "auf dem Platz liegen die drei Plaetze, die"
                + " Kante, die Fahrbahn und ihr Bordstein: " + flaeche.keySet());
    }

    @Test
    @DisplayName("Die Mauer ist GESCHLOSSEN - die Radialgasse endet davor")
    void dieMauerIstGeschlossen() {
        Stadtplan plan = Stadtplan.altfurt();

        // Auf der Achse, durch jedes Band hindurch - bis zur Mauer.
        for (int r = 52; r < plan.mauerVon(); r++) {
            assertEquals(Stadtplan.Lage.RADIALGASSE,
                    plan.lageBei(Stadtplan.MITTE_X + r, Stadtplan.MITTE_Z),
                    "Ostgasse auf Ring " + r);
            assertEquals(Stadtplan.Lage.RADIALGASSE,
                    plan.lageBei(Stadtplan.MITTE_X, Stadtplan.MITTE_Z - r),
                    "Nordgasse auf Ring " + r);
        }
        // UND DANN IST MAUER, auch auf der Achse: Die Portale in die anderen Welten
        // stehen in den Torhaeusern, und ein Torhaus ist ein Bau, der das Stueck Mauer
        // ersetzt, auf dem er steht. Ein Loch braucht es dafuer nicht.
        for (int r = plan.mauerVon(); r <= plan.aussenRing(); r++) {
            assertEquals(Stadtplan.Lage.MAUER,
                    plan.lageBei(Stadtplan.MITTE_X + r, Stadtplan.MITTE_Z),
                    "Ostmauer auf Ring " + r);
            assertEquals(Stadtplan.Lage.MAUER,
                    plan.lageBei(Stadtplan.MITTE_X, Stadtplan.MITTE_Z - r),
                    "Nordmauer auf Ring " + r);
            assertEquals(Stadtplan.Lage.MAUER,
                    plan.lageBei(Stadtplan.MITTE_X - r, Stadtplan.MITTE_Z),
                    "Westmauer auf Ring " + r);
            assertEquals(Stadtplan.Lage.MAUER,
                    plan.lageBei(Stadtplan.MITTE_X, Stadtplan.MITTE_Z + r),
                    "Suedmauer auf Ring " + r);
        }
        // KEINE EINZIGE LUECKE im ganzen Mauerring - abgeschritten, nicht gestichprobt.
        int luecken = 0;
        for (int r = plan.mauerVon(); r <= plan.aussenRing(); r++) {
            for (int d = -r; d <= r; d++) {
                if (plan.lageBei(Stadtplan.MITTE_X + r, Stadtplan.MITTE_Z + d)
                        != Stadtplan.Lage.MAUER) {
                    luecken++;
                }
                if (plan.lageBei(Stadtplan.MITTE_X + d, Stadtplan.MITTE_Z - r)
                        != Stadtplan.Lage.MAUER) {
                    luecken++;
                }
            }
        }
        assertEquals(0, luecken, "die Mauer hat kein Loch");
        // UND IM KERN GILT DIE GASSE SEHR WOHL [24.11.2026]: Bis heute hiess es hier
        // "Und im Kern gilt die Gasse nicht: Dort steht Handarbeit." Genau das war der
        // Befund des Betreibers - der Weg zum Tor begann erst auf Ring 52, und von der
        // Marktmitte kam man in keine Himmelsrichtung gerade hinaus. Der Kreuzarm
        // durchschneidet den Kern jetzt; wo ein eingetragener Bau ihn ueberspannt, geht
        // er als LAUBE hindurch.
        assertEquals(Stadtplan.Lage.RADIALGASSE,
                plan.lageBei(Stadtplan.MITTE_X, Stadtplan.MITTE_Z - 45),
                "der Arm laeuft durch den Kern");
        // NEBEN dem Arm ist der Kern unveraendert Kern.
        assertEquals(Stadtplan.Lage.KERN,
                plan.lageBei(Stadtplan.MITTE_X + NEBEN_DER_ACHSE,
                        Stadtplan.MITTE_Z - 45),
                "neben dem Arm steht Handarbeit");
    }

    @Test
    @DisplayName("Die Quartiersecke bleibt ein Platz - dort stiessen zwei Zeilen zusammen")
    void dieEcke() {
        Stadtplan plan = Stadtplan.altfurt();

        // Die Ecke des Rings 67 - beide Abstaende gleich gross.
        assertEquals(Stadtplan.Lage.ECKPLATZ,
                plan.lageBei(Stadtplan.MITTE_X + 67, Stadtplan.MITTE_Z + 67), "die Ecke");
        // DAS INNERE BAND 57..67 reicht laengs bis 57 - ECKE_FREI = 51. Alles
        // darueber ist Platz, UEBER DAS GANZE BAND und nicht nur auf dem innersten
        // Ring: Vorher rechnete die Ecke mit dem Ring der gefragten Stelle, und
        // dieser Block hier hiess ZEILE, obwohl keine Zeile ihn erreicht.
        assertEquals(51, Stadtplan.zeilenreichweite(57), "die Reichweite des Bandes");
        assertEquals(Stadtplan.Lage.ECKPLATZ,
                plan.lageBei(Stadtplan.MITTE_X - 67, Stadtplan.MITTE_Z + 60),
                "noch im Freiraum");
        assertEquals(Stadtplan.Lage.ECKPLATZ,
                plan.lageBei(Stadtplan.MITTE_X - 67, Stadtplan.MITTE_Z + 52),
                "einen Block jenseits der Reichweite");
        // Bis zur Reichweite ist Bauland.
        assertEquals(Stadtplan.Lage.ZEILE,
                plan.lageBei(Stadtplan.MITTE_X - 67, Stadtplan.MITTE_Z + 51),
                "genau auf der Reichweite");
        // DAS AEUSSERE BAND hat seine eigene, GROESSERE Reichweite - 68 - 6 = 62.
        assertEquals(62, Stadtplan.zeilenreichweite(68), "das aeussere Band");
        // GEMESSEN IN DER MITTE DES BANDES und nicht an seinem Rand [24.11.2026]:
        // Am Rand frisst der Gassenbogen bis KRUMM_TIEFE Bloecke in das Band hinein -
        // dort ist die Lage von der Welle abhaengig und nicht von der Eckregel. Ring 73
        // liegt fuenf Bloecke tief im Band 68..78, also weit jenseits des Bogens.
        int bandmitte = plan.zeileAussenVon(0) + Stadtplan.ZEILE_TIEFE / 2;
        assertEquals(73, bandmitte, "die Mitte des aeusseren Bandes");
        assertEquals(Stadtplan.Lage.ZEILE,
                plan.lageBei(Stadtplan.MITTE_X + bandmitte, Stadtplan.MITTE_Z + 62),
                "dort reicht die Zeile weiter");
        assertEquals(Stadtplan.Lage.ECKPLATZ,
                plan.lageBei(Stadtplan.MITTE_X + bandmitte, Stadtplan.MITTE_Z + 63),
                "und einen Block weiter nicht mehr");
        // Eine GASSE bekommt keinen Eckplatz - sie laeuft um die Ecke herum.
        assertEquals(Stadtplan.Lage.RINGGASSE,
                plan.lageBei(Stadtplan.MITTE_X + 54, Stadtplan.MITTE_Z + 54),
                "die Ecke der Ringgasse");
    }

    @Test
    @DisplayName("Kein Haus beruehrt ein anderes")
    void keineUeberschneidung() {
        List<Platzhalterbau> haeuser = Stadtplan.altfurt().haeuser();
        assertTrue(haeuser.size() >= 100, "eine Stadt, kein Weiler: " + haeuser.size());

        List<String> treffer = new ArrayList<>();
        for (int i = 0; i < haeuser.size(); i++) {
            for (int j = i + 1; j < haeuser.size(); j++) {
                if (haeuser.get(i).bereich().schneidet(haeuser.get(j).bereich())) {
                    treffer.add(haeuser.get(i).schluessel() + " <-> "
                            + haeuser.get(j).schluessel());
                }
            }
        }
        assertEquals(List.of(), treffer, "zwei Bauten mit gemeinsamen Bloecken werden"
                + " BEIDE verworfen - eine Ueberschneidung kostet zwei Haeuser");
    }

    @Test
    @DisplayName("Jeder Hausblock liegt in einer Zeile - keiner auf Gasse, Ecke oder Mauer")
    void haeuserNurAufBauland() {
        Stadtplan plan = Stadtplan.altfurt();
        Map<Stadtplan.Lage, Integer> falsch = new EnumMap<>(Stadtplan.Lage.class);
        String erstes = null;

        for (Platzhalterbau bau : plan.haeuser()) {
            Quader q = bau.bereich();
            for (int x = q.vonX(); x <= q.bisX(); x++) {
                for (int z = q.vonZ(); z <= q.bisZ(); z++) {
                    Stadtplan.Lage lage = plan.lageBei(x, z);
                    if (lage != Stadtplan.Lage.ZEILE) {
                        falsch.merge(lage, 1, Integer::sum);
                        if (erstes == null) {
                            erstes = bau.schluessel() + " auf " + x + "/" + z;
                        }
                    }
                }
            }
        }
        assertEquals(Map.of(), falsch, "ein Haus auf der Gasse versperrt sie, eines auf"
                + " der Mauer reisst ein Loch: " + erstes);
    }

    @Test
    @DisplayName("Der Kern bleibt unberuehrt - dort steht Handarbeit")
    void derKernBleibtFrei() {
        for (Platzhalterbau bau : Stadtplan.altfurt().haeuser()) {
            Quader q = bau.bereich();
            int naechster = Integer.MAX_VALUE;
            for (int x = q.vonX(); x <= q.bisX(); x++) {
                for (int z = q.vonZ(); z <= q.bisZ(); z++) {
                    naechster = Math.min(naechster, Stadtplan.ring(x, z));
                }
            }
            assertTrue(naechster > Stadtplan.KERN_RING, bau.schluessel() + " reicht bis"
                    + " Ring " + naechster + " - das Bundesratshaus steht bis 25");
        }
    }

    @Test
    @DisplayName("Vor jeder Tuer liegt eine Gasse - kein Haus oeffnet in den Hof")
    void jedeTuerAnDieGasse() {
        Stadtplan plan = Stadtplan.altfurt();

        for (Platzhalterbau bau : plan.haeuser()) {
            Quader q = bau.bereich();
            // Der Block VOR der Tuerseite, einen Schritt in Tuerrichtung.
            int x = switch (bau.tuer()) {
                case OST -> q.bisX() + 1;
                case WEST -> q.vonX() - 1;
                default -> q.mitteX();
            };
            int z = switch (bau.tuer()) {
                case SUED -> q.bisZ() + 1;
                case NORD -> q.vonZ() - 1;
                default -> q.mitteZ();
            };
            Stadtplan.Lage davor = plan.lageBei(x, z);
            // ZEILE ist erlaubt, wenn das Haus zurueckgesetzt steht - dann liegt sein
            // Vorgarten davor. Gasse, Eckplatz oder Vorgarten: nur kein fremdes Haus,
            // keine Mauer und kein Kern.
            // SEIT DEM 24.11.2026 IST DAS MEIST DER BORDSTEIN: Die Kante der Gasse
            // liegt genau vor den Hausfronten, also oeffnet die Tuer auf ein Trottoir.
            // Gefragt wird darum istWeg und nicht RINGGASSE - die EINE Stelle, die
            // sagt, was ein Weg ist.
            assertTrue(Stadtplan.istWeg(davor) || davor == Stadtplan.Lage.ZEILE
                            || davor == Stadtplan.Lage.ECKPLATZ,
                    bau.schluessel() + " oeffnet auf " + davor + " bei " + x + "/" + z);

            // UND DIE RICHTUNG IST DIE DES EIGENEN BANDES: Die innere Zeile oeffnet
            // zur Mitte, die aeussere nach aussen. Ohne diese Zusage liessen sich
            // beide VERTAUSCHEN, und jedes Haus stuende mit der Tuer zum Hof des
            // Nachbarn - die Lage davor waere immer noch ZEILE oder Gasse.
            int querTuer = Math.max(Math.abs(x - Stadtplan.MITTE_X),
                    Math.abs(z - Stadtplan.MITTE_Z));
            int querMitte = Math.max(Math.abs(q.mitteX() - Stadtplan.MITTE_X),
                    Math.abs(q.mitteZ() - Stadtplan.MITTE_Z));
            assertTrue(querTuer != querMitte, bau.schluessel()
                    + ": die Tuer liegt nicht in der Mitte des Hauses");
            boolean oeffnetNachAussen = querTuer > querMitte;
            boolean aeussereZeile = bau.schluessel().charAt(7) == 'a';
            assertEquals(aeussereZeile, oeffnetNachAussen, bau.schluessel()
                    + ": die aeussere Zeile oeffnet nach aussen, die innere zur"
                    + " Mitte - Tuer bei Ring " + querTuer + ", Hausmitte bei "
                    + querMitte);
        }
    }

    @Test
    @DisplayName("Die Haeuser stehen in geschlossenen Zeilen - genau eine Luecke je Zeile")
    void geschlosseneZeilen() {
        Stadtplan plan = Stadtplan.altfurt();

        // Nach Zeile gruppiert: "haus-n0i-03" -> "haus-n0i".
        Map<String, List<Platzhalterbau>> zeilen = new HashMap<>();
        for (Platzhalterbau bau : plan.haeuser()) {
            zeilen.computeIfAbsent(bau.schluessel().substring(0, 8),
                    unbenutzt -> new ArrayList<>()).add(bau);
        }
        // Zwei Quartiere, vier Seiten, je zwei Zeilen.
        assertEquals(16, zeilen.size(), "so viele Zeilen: " + zeilen.keySet());

        for (Map.Entry<String, List<Platzhalterbau>> zeile : zeilen.entrySet()) {
            boolean laengsIstX = zeile.getKey().charAt(5) == 'n'
                    || zeile.getKey().charAt(5) == 's';
            List<Platzhalterbau> reihe = new ArrayList<>(zeile.getValue());
            reihe.sort(Comparator.comparingInt(
                    bau -> laengsIstX ? bau.bereich().vonX() : bau.bereich().vonZ()));
            assertTrue(reihe.size() >= 4, zeile.getKey() + " hat nur " + reihe.size()
                    + " Haeuser");

            List<Integer> luecken = new ArrayList<>();
            int anschliessend = 0;
            for (int i = 1; i < reihe.size(); i++) {
                Quader vorher = reihe.get(i - 1).bereich();
                Quader jetzt = reihe.get(i).bereich();
                int lueckeBeginnt = (laengsIstX ? vorher.bisX() : vorher.bisZ()) + 1;
                int lueckeEndet = (laengsIstX ? jetzt.vonX() : jetzt.vonZ()) - 1;
                int breite = lueckeEndet - lueckeBeginnt + 1;
                if (breite > 0) {
                    luecken.add(breite);
                } else {
                    anschliessend++;
                }
            }
            // DIE ZEILE IST GESCHLOSSEN, ABER NICHT LUECKENLOS [24.11.2026]:
            // Vorher hielt diese Probe GENAU EINE Luecke je Zeile - die
            // Radialgasse. Das war die Antwort auf "die haeuser auch verbinden",
            // und sie stimmt weiter: Die meisten Nachbarn teilen ihre Giebelwand.
            // Aber eine Zeile OHNE jede Luecke ist eine Mauer aus Haeusern, und der
            // Betreiber hat die erste Stadt "gleichmaessig und repetetativ"
            // genannt. Jede neunte Parzelle bleibt darum ein Hof.
            // MEHR ANSCHLUESSE ALS LUECKEN, in JEDER Zeile. Gemessen am 24.11.2026
            // ist die schwaechste Zeile 7 zu 4; ueber die ganze Stadt schliessen 85
            // von 100 Nachbarn an. Die Zahl steht hier als UNTERGRENZE, nicht als
            // Messwert - eine Zeile, in der die Luecken ueberwiegen, ist keine Zeile.
            assertTrue(anschliessend > luecken.size(),
                    zeile.getKey() + ": " + anschliessend + " anschliessende"
                            + " Nachbarn gegen " + luecken.size() + " Luecken "
                            + luecken + " - die Zeile faellt auseinander");
            assertTrue(!luecken.isEmpty(),
                    zeile.getKey() + " hat keine einzige Luecke - und damit nicht"
                            + " einmal die Radialgasse");
            for (int breite : luecken) {
                assertTrue(breite <= 2 * Stadtplan.BREITEN.get(
                                Stadtplan.BREITEN.size() - 1) + 2 * Stadtplan.RADIAL_HALB + 1,
                        zeile.getKey() + ": eine Luecke von " + breite
                                + " Bloecken ist kein Hof mehr, sondern ein Loch");
            }
        }
    }

    @Test
    @DisplayName("Jeder Name kommt einmal vor")
    void namenEindeutig() {
        List<Platzhalterbau> haeuser = Stadtplan.altfurt().haeuser();
        Set<String> namen = new HashSet<>();
        List<String> doppelt = new ArrayList<>();
        for (Platzhalterbau bau : haeuser) {
            if (!namen.add(bau.schluessel())) {
                doppelt.add(bau.schluessel());
            }
        }
        assertEquals(List.of(), doppelt, "ein doppelter Name holt beim Sichern das"
                + " falsche Haus");
        assertEquals(haeuser.size(), namen.size(), "so viele Namen wie Haeuser");
    }

    @Test
    @DisplayName("Der zweite Lauf setzt dieselbe Stadt")
    void immerDieselbeStadt() {
        // KEIN ZUFALL: Nach einem Neustart muessen dieselben Haeuser dastehen - sonst
        // kennt niemand seine Gasse wieder, und keine Probe koennte eine Breite
        // nachrechnen.
        assertEquals(Stadtplan.altfurt().haeuser(), new Stadtplan(2).haeuser(),
                "zwei Plaene, dieselbe Stadt");
        assertNotSame(Stadtplan.altfurt().haeuser(), new Stadtplan(2).haeuser(),
                "zwei Plaene geben nicht dieselbe Liste heraus");
    }

    @Test
    @DisplayName("Die Stadt wechselt Breite, Tiefe, Hoehe und Baustoff")
    void dieStadtWechselt() {
        List<Platzhalterbau> haeuser = Stadtplan.altfurt().haeuser();
        Set<Integer> breiten = new HashSet<>();
        Set<Integer> tiefen = new HashSet<>();
        Set<Integer> hoehen = new HashSet<>();
        Set<Baustil> stile = new HashSet<>();
        int kupfer = 0;

        for (Platzhalterbau bau : haeuser) {
            Quader q = bau.bereich();
            boolean laengsIstX = bau.tuer() == Richtung.NORD || bau.tuer() == Richtung.SUED;
            int kx = q.bisX() - q.vonX() + 1;
            int kz = q.bisZ() - q.vonZ() + 1;
            breiten.add(laengsIstX ? kx : kz);
            tiefen.add(laengsIstX ? kz : kx);
            hoehen.add(q.bisY() - q.vonY() + 1);
            stile.add(bau.stil());
            if (bau.dach() == Dachart.KUPFER) {
                kupfer++;
            }
        }
        // JEDE angebotene Moeglichkeit kommt vor - sonst waehlt die Streuung nicht.
        assertEquals(new HashSet<>(Stadtplan.BREITEN), breiten, "alle Breiten");
        // DIE TIEFEN KOENNEN GEDECKELT SEIN [24.11.2026]: Wo die Gassenwelle ausbiegt,
        // wird ein Haus flacher, damit es ihr folgen kann - aus einer 11 wird dann eine
        // 10, obwohl die 10 in TIEFEN nicht steht. Die Zusage ist darum: jede
        // angebotene Tiefe kommt vor, und alles Vorkommende liegt in der Spanne.
        assertTrue(tiefen.containsAll(Stadtplan.TIEFEN),
                "jede angebotene Tiefe kommt vor: " + tiefen);
        int flachste = Stadtplan.TIEFEN.get(0);
        int tiefste = Stadtplan.TIEFEN.get(Stadtplan.TIEFEN.size() - 1);
        for (int tiefe : tiefen) {
            assertTrue(tiefe >= flachste && tiefe <= tiefste,
                    "die Tiefe " + tiefe + " liegt ausserhalb von " + flachste + ".."
                            + tiefste);
        }
        // UND DIE FLACHSTE IST WIRKLICH FLACH: Aus ihr kommt der Platz fuer die
        // Baulinie - ohne sie koennte kein Haus zurueckspringen.
        assertTrue(tiefste - flachste >= Stadtplan.SPRUNG_TIEFE,
                "zwischen der flachsten und der tiefsten Tiefe liegt mindestens ein"
                        + " ganzer Sprung: " + flachste + ".." + tiefste);
        // DIE HOEHEN KOMMEN AUS DEM GEFAELLE [24.11.2026]: innen hoch, aussen
        // niedrig, dazu ein Nest- und ein Hausspiel. Eine feste Liste gibt es nicht
        // mehr - sie machte jede Zeile so hoch wie jede andere.
        assertTrue(hoehen.size() >= 4, "mindestens vier verschiedene Hoehen: "
                + hoehen);
        for (int hoehe : hoehen) {
            assertTrue(hoehe >= Stadtplan.HOEHE_AUSSEN - 1
                            && hoehe <= Stadtplan.HOEHE_INNEN + 2,
                    "Hoehe " + hoehe + " liegt ausserhalb des Gefaelles "
                            + Stadtplan.HOEHE_AUSSEN + ".." + Stadtplan.HOEHE_INNEN);
        }
        assertEquals(new HashSet<>(Stadtplan.STILE), stile, "alle Stile");
        // GOLD NICHT: Es ist die Auszeichnung des Muenzhauses.
        assertFalse(stile.contains(Baustil.GOLD), "Gold bleibt dem Muenzhaus");
        // Kupfer ist selten, aber es kommt vor.
        assertTrue(kupfer > 0, "kein einziges Kupferdach");
        assertTrue(kupfer * 3 < haeuser.size(), "Kupfer ist keine Auszeichnung mehr: "
                + kupfer + " von " + haeuser.size());
    }

    @Test
    @DisplayName("Jede Breite ist ungerade - ein Giebel braucht eine Mitte")
    void ungeradeBreiten() {
        for (int breite : Stadtplan.BREITEN) {
            assertEquals(1, breite % 2, "Breite " + breite + " laesst einen First von"
                    + " zwei Bloecken statt einer Kante");
        }
        for (Platzhalterbau bau : Stadtplan.altfurt().haeuser()) {
            assertEquals(1, bau.front() % 2, bau.schluessel() + " ist "
                    + bau.front() + " breit zur Gasse");
        }
    }

    @Test
    @DisplayName("Ein Quartier muss es mindestens sein")
    void ohneQuartierKeineStadt() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new Stadtplan(0)).getMessage().contains("mindestens ein Quartier"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new Stadtplan(-1)).getMessage().contains("mindestens ein"));
        // Mit einem Quartier endet die Stadt schon bei Ring 86.
        Stadtplan klein = new Stadtplan(1);
        assertEquals(79, klein.abschlussgasseVon(), "die Gasse am Wehrgang");
        assertEquals(84, klein.mauerVon(), "die Mauer");
        assertEquals(86, klein.aussenRing(), "der letzte Ring der Stadt");
        assertTrue(klein.haeuser().size() < Stadtplan.altfurt().haeuser().size(),
                "ein Quartier traegt weniger Haeuser als zwei");
    }

    @Test
    @DisplayName("Der Umriss ist die Flaeche, die das Pflaster abgeht")
    void derUmriss() {
        Stadtplan plan = Stadtplan.altfurt();
        Quader umriss = plan.umriss();

        // DIE MAUER ENDET AUF RING 113, die TUERME treten zwei Bloecke davor - und
        // der Umriss ist die Flaeche, die abgegangen wird. Was nicht darin liegt, wird
        // nie gesetzt: Ein Turm ausserhalb stuende im Plan und nicht in der Welt.
        assertEquals(113, plan.aussenRing(), "die Aussenkante der Mauer");
        assertEquals(2, Stadtplan.TURM_VOR, "so weit treten die Tuerme vor");
        assertEquals(Stadtplan.MITTE_X - 115, umriss.vonX(), "Westrand");
        assertEquals(Stadtplan.MITTE_X + 115, umriss.bisX(), "Ostrand");
        assertEquals(Stadtplan.MITTE_Z - 115, umriss.vonZ(), "Nordrand");
        assertEquals(Stadtplan.MITTE_Z + 115, umriss.bisZ(), "Suedrand");
        // NUR DIE BODENEBENE: Was darueber steht, sind Haeuser und Mauer.
        assertEquals(Stadtplan.BODEN_Y, umriss.vonY(), "Boden");
        assertEquals(Stadtplan.BODEN_Y, umriss.bisY(), "und nur er");
        assertEquals(231L * 231L, umriss.bloecke(), "231 mal 231 Bloecke Pflaster");

        // UND DIE STADT PASST IN DIE WELTGRENZE: Altfurt hat kante: 800, also x und z
        // von -400 bis 400.
        assertTrue(umriss.vonX() > -400 && umriss.bisX() < 400, "x in der Weltgrenze");
        assertTrue(umriss.vonZ() > -400 && umriss.bisZ() < 400, "z in der Weltgrenze");
    }

    @Test
    @DisplayName("Kein Haus liegt ausserhalb des Umrisses")
    void haeuserImUmriss() {
        Stadtplan plan = Stadtplan.altfurt();
        Quader umriss = plan.umriss();
        for (Platzhalterbau bau : plan.haeuser()) {
            Quader q = bau.bereich();
            assertTrue(q.vonX() >= umriss.vonX() && q.bisX() <= umriss.bisX(),
                    bau.schluessel() + " liegt bei x " + q.vonX() + ".." + q.bisX());
            assertTrue(q.vonZ() >= umriss.vonZ() && q.bisZ() <= umriss.bisZ(),
                    bau.schluessel() + " liegt bei z " + q.vonZ() + ".." + q.bisZ());
            assertEquals(Stadtplan.BODEN_Y, q.vonY(), bau.schluessel()
                    + " beginnt nicht auf dem Boden");
        }
    }

    @Test
    @DisplayName("Wo keine Zeile hinreicht, ist Platz - gemessen an den wirklichen Haeusern")
    void keinBaulandJenseitsDerZeilen() {
        Stadtplan plan = Stadtplan.altfurt();

        // DIE ERWARTUNG KOMMT AUS DEN HAEUSERN und nicht aus der Regel: Je Band und
        // Seite, wie weit das aeusserste Haus laengs der Gasse reicht. Ein Block, der
        // weiter draussen liegt als dieses Haus PLUS die breiteste Hausbreite, kann von
        // keiner Zeile mehr erreicht werden - dort MUSS Platz sein.
        int breiteste = 0;
        for (int breite : Stadtplan.BREITEN) {
            breiteste = Math.max(breiteste, breite);
        }
        Map<String, Integer> weiteste = new HashMap<>();
        for (Platzhalterbau bau : plan.haeuser()) {
            Quader q = bau.bereich();
            boolean laengsIstX = bau.tuer() == Richtung.NORD || bau.tuer() == Richtung.SUED;
            int laengs = laengsIstX
                    ? Math.max(Math.abs(q.vonX() - Stadtplan.MITTE_X),
                            Math.abs(q.bisX() - Stadtplan.MITTE_X))
                    : Math.max(Math.abs(q.vonZ() - Stadtplan.MITTE_Z),
                            Math.abs(q.bisZ() - Stadtplan.MITTE_Z));
            int quer = laengsIstX
                    ? Math.min(Math.abs(q.vonZ() - Stadtplan.MITTE_Z),
                            Math.abs(q.bisZ() - Stadtplan.MITTE_Z))
                    : Math.min(Math.abs(q.vonX() - Stadtplan.MITTE_X),
                            Math.abs(q.bisX() - Stadtplan.MITTE_X));
            String band = bandNummer(plan, quer) + "";
            weiteste.merge(band, laengs, Math::max);
        }
        assertEquals(4, weiteste.size(), "vier Baender tragen Haeuser: " + weiteste);

        int geprueft = 0;
        Quader umriss = plan.umriss();
        for (int x = umriss.vonX(); x <= umriss.bisX(); x++) {
            for (int z = umriss.vonZ(); z <= umriss.bisZ(); z++) {
                Stadtplan.Lage lage = plan.lageBei(x, z);
                if (lage != Stadtplan.Lage.ZEILE) {
                    continue;
                }
                int dx = Math.abs(x - Stadtplan.MITTE_X);
                int dz = Math.abs(z - Stadtplan.MITTE_Z);
                int quer = Math.max(dx, dz);
                int laengs = Math.min(dx, dz);
                Integer grenze = weiteste.get(bandNummer(plan, quer) + "");
                assertNotNull(grenze, "Band zu " + quer);
                // Ein Bauland-Block darf nie weiter draussen liegen als das aeusserste
                // Haus seines Bandes plus eine Hausbreite - sonst ist er unerreichbar.
                assertTrue(laengs <= grenze + breiteste,
                        x + "/" + z + " heisst ZEILE, aber das aeusserste Haus des Bandes"
                                + " reicht nur bis " + grenze);
                geprueft++;
            }
        }
        // GEZAEHLT: Ohne Durchlaeufe meldete diese Probe gruen, ohne etwas zu pruefen.
        assertTrue(geprueft > 5000, "so viele Bauland-Bloecke geprueft: " + geprueft);
    }

    /** Die Nummer des Bandes, in dem dieser Ring liegt - vier bei zwei Quartieren. */
    private static int bandNummer(Stadtplan plan, int ring) {
        for (int q = 0; q < plan.quartiere(); q++) {
            if (ring < plan.zeileAussenVon(q)) {
                return 2 * q;
            }
            if (ring < plan.quartierAnfang(q + 1)) {
                return 2 * q + 1;
            }
        }
        return -1;
    }


    @Test
    @DisplayName("Die Mauer ist sechs hoch, und die Zinnen wechseln sich ab")
    void dieMauerUndIhreZinnen() {
        Stadtplan plan = Stadtplan.altfurt();
        int mauer = plan.mauerVon();
        int aussen = plan.aussenRing();

        // DIE DICKE: drei Ringe, und nur der aeusserste traegt Zinnen.
        assertEquals(mauer + Stadtplan.MAUER_DICKE - 1, aussen, "drei Ringe dick");
        for (int r = mauer; r < aussen; r++) {
            assertEquals(Stadtplan.MAUER_HOEHE - 1,
                    plan.mauerhoehe(Stadtplan.MITTE_X + r, Stadtplan.MITTE_Z + 8),
                    "der innere Mauerring " + r + " traegt keine Zinne");
        }

        // DIE ZINNEN WECHSELN SICH AB - gezaehlt ueber eine ganze Strecke und nicht an
        // zwei Stellen: Eine Regel, die immer false (oder immer true) gibt, haette zwei
        // Stellen vielleicht noch getroffen.
        // AUCH AUF DER ACHSE STEHT MAUER: Sie ist geschlossen. Das Torhaus ist ein
        // Bau, der sie ersetzt, wo er steht - sie braucht kein Loch.
        for (int z = Stadtplan.MITTE_Z - 4; z <= Stadtplan.MITTE_Z + 4; z++) {
            assertTrue(plan.mauerhoehe(Stadtplan.MITTE_X + aussen, z) > 0,
                    "die Mauer steht auch bei z " + z);
        }

        // AN DEN TUERMEN VORBEI [24.11.2026]: Seit es Mauertuerme gibt, ist die Krone
        // nicht mehr durchweg sechs hoch. Geprueft wird darum PAARWEISE und nicht
        // gezaehlt: Zwei benachbarte Bloecke GEWOEHNLICHER Mauer muessen verschieden
        // hoch sein. Eine Zaehlung auf 37 Wechsel waere an jeder Turmluecke rot
        // geworden, obwohl die Brustwehr stimmt - und sie liesse sich durch Hochdrehen
        // der Zahl beschwichtigen.
        //
        // GEZAEHLT WIRD TROTZDEM, aber anderes: wie viele gewoehnliche Mauerbloecke und
        // wie viele Turmbloecke die Strecke gesehen hat. Ohne das waere eine Strecke,
        // die nur ueber Tuerme laeuft, stillschweigend gruen.
        int hoch = 0;
        int tief = 0;
        int imTurm = 0;
        for (int z = Stadtplan.MITTE_Z + 3; z <= Stadtplan.MITTE_Z + 40; z++) {
            int x = Stadtplan.MITTE_X + aussen;
            if (plan.imTurm(x, z)) {
                imTurm++;
                continue;
            }
            int hoehe = plan.mauerhoehe(x, z);
            assertTrue(hoehe == Stadtplan.MAUER_HOEHE || hoehe == Stadtplan.MAUER_HOEHE - 1,
                    "Mauerhoehe bei z " + z + " ist " + hoehe);
            if (hoehe == Stadtplan.MAUER_HOEHE) {
                hoch++;
            } else {
                tief++;
            }
            // DER NACHBAR IST ANDERS - eine Zinne, eine Luecke. Gefragt nur, wenn der
            // Nachbar auch gewoehnliche Mauer ist.
            if (!plan.imTurm(x, z + 1) && plan.mauerhoehe(x, z + 1) > 0) {
                assertNotEquals(hoehe, plan.mauerhoehe(x, z + 1),
                        "bei z " + z + " und " + (z + 1) + " steht dieselbe Hoehe");
            }
        }
        assertEquals(38, hoch + tief + imTurm, "38 Bloecke abgeschritten");
        assertTrue(imTurm > 0, "die Strecke laeuft an mindestens einem Turm vorbei -"
                + " sonst prueft sie die Tuerme nicht einmal versehentlich");
        assertTrue(hoch >= 10 && tief >= 10, "etwa halbe halbe: " + hoch + "/" + tief);

        // UND AUF DER ANDEREN ACHSE AUCH: Eine Regel, die nur x fragt, gaebe der Nord-
        // und Suedmauer eine geschlossene Brustwehr.
        int gepruefteNachbarn = 0;
        for (int x = Stadtplan.MITTE_X + 3; x <= Stadtplan.MITTE_X + 40; x++) {
            int z = Stadtplan.MITTE_Z - aussen;
            if (plan.imTurm(x, z) || plan.imTurm(x + 1, z)) {
                continue;
            }
            assertNotEquals(plan.mauerhoehe(x, z), plan.mauerhoehe(x + 1, z),
                    "an der Nordmauer steht bei x " + x + " zweimal dieselbe Hoehe");
            gepruefteNachbarn++;
        }
        assertTrue(gepruefteNachbarn > 20,
                "so viele Nachbarpaare an der Nordmauer geprueft: " + gepruefteNachbarn);

        // WO KEINE MAUER IST, IST AUCH KEINE HOEHE.
        assertEquals(0, plan.mauerhoehe(Stadtplan.MITTE_X, Stadtplan.MITTE_Z),
                "die Mitte des Marktes");
        assertTrue(plan.mauerhoehe(Stadtplan.MITTE_X + aussen, Stadtplan.MITTE_Z) > 0,
                "die Mauer ist auf der Achse geschlossen");
        assertEquals(0, plan.mauerhoehe(Stadtplan.MITTE_X + aussen + 1,
                Stadtplan.MITTE_Z + 8), "jenseits der Stadt");
    }

    @Test
    @DisplayName("Die Zinne haengt an x UND z - sonst ist eine Mauerseite geschlossen")
    void dieZinneHaengtAnBeiden() {
        // GERECHNET und nicht aus zinne() geholt: Jeder zweite Block, und zwar auf
        // JEDER Achse. Eine Regel, die nur x fragt, waere auf der Ost- und Westmauer
        // (wo x fest ist) durchgehend gleich.
        assertTrue(Stadtplan.zinne(0, 0), "0 + 0 ist gerade");
        assertFalse(Stadtplan.zinne(1, 0), "1 + 0 ist ungerade");
        assertFalse(Stadtplan.zinne(0, 1), "0 + 1 ist ungerade - z wirkt also auch");
        assertTrue(Stadtplan.zinne(1, 1), "1 + 1 ist gerade");
        // UND AUCH IM NEGATIVEN: der Rest von -1 ist in Java negativ, darum wird mit
        // dem Bit gerechnet und nicht mit dem Rest.
        assertFalse(Stadtplan.zinne(-1, 0), "-1 + 0 ist ungerade");
        assertTrue(Stadtplan.zinne(-1, -1), "-1 + -1 ist gerade");
        assertTrue(Stadtplan.zinne(-2, 0), "-2 + 0 ist gerade");
    }


    @Test
    @DisplayName("Die Stadt ist GEWACHSEN: Nester, Hoefe und ein Hoehengefaelle")
    void gewachsenUndNichtGleichmaessig() {
        Stadtplan plan = Stadtplan.altfurt();

        // WARUM ES DIESE PROBE GIBT [24.11.2026]: Der Betreiber sah die erste Stadt an
        // und sagte "aktuell ist alles noch recht gleichmaessig und repetetativ. es
        // sollte ja eher eine gewachsene stadt sein." Die Ursache war die Streuung JE
        // HAUS - jedes Haus wuerfelte fuer sich, und das Ergebnis sieht gesprenkelt aus,
        // nicht gewachsen. Drei Dinge machen den Unterschied, und alle drei stehen hier.

        // 1. NESTER: Nachbarhaeuser teilen ihren Baustoff. Gezaehlt wird ueber alle
        //    Nachbarpaare einer Zeile; bei reiner Streuung je Haus waere der Anteil
        //    gleicher Nachbarn etwa 1 von 4 (vier Stile), hier muss er deutlich darueber
        //    liegen.
        Map<String, List<Platzhalterbau>> zeilen = new HashMap<>();
        for (Platzhalterbau bau : plan.haeuser()) {
            zeilen.computeIfAbsent(bau.schluessel().substring(0, 8),
                    unbenutzt -> new ArrayList<>()).add(bau);
        }
        int paare = 0;
        int gleicherStil = 0;
        int gleicheBreite = 0;
        for (Map.Entry<String, List<Platzhalterbau>> zeile : zeilen.entrySet()) {
            boolean laengsIstX = zeile.getKey().charAt(5) == 'n'
                    || zeile.getKey().charAt(5) == 's';
            List<Platzhalterbau> reihe = new ArrayList<>(zeile.getValue());
            reihe.sort(Comparator.comparingInt(
                    bau -> laengsIstX ? bau.bereich().vonX() : bau.bereich().vonZ()));
            for (int i = 1; i < reihe.size(); i++) {
                paare++;
                if (reihe.get(i).stil() == reihe.get(i - 1).stil()) {
                    gleicherStil++;
                }
                if (reihe.get(i).front() == reihe.get(i - 1).front()) {
                    gleicheBreite++;
                }
            }
        }
        assertTrue(paare > 200, "so viele Nachbarpaare: " + paare);
        assertTrue(gleicherStil * 2 > paare,
                "ueber die Haelfte der Nachbarn muss denselben Baustoff haben - sonst ist"
                        + " es Streuung und kein Nest: " + gleicherStil + " von " + paare);
        assertTrue(gleicheBreite * 2 > paare,
                "und dieselbe Breite: " + gleicheBreite + " von " + paare);
        // UND NICHT ALLE: Eine Stadt aus einem Baustoff ist auch nicht gewachsen.
        assertTrue(gleicherStil < paare,
                "es muss auch Spruenge geben: " + gleicherStil + " von " + paare);

        // 2. HOEFE: Es gibt Luecken, die NICHT die Radialgasse sind. Erkannt werden sie
        //    daran, dass sie nicht auf der Mittelachse liegen.
        int hoefe = 0;
        int anschluesse = 0;
        for (Map.Entry<String, List<Platzhalterbau>> zeile : zeilen.entrySet()) {
            boolean laengsIstX = zeile.getKey().charAt(5) == 'n'
                    || zeile.getKey().charAt(5) == 's';
            int mitte = laengsIstX ? Stadtplan.MITTE_X : Stadtplan.MITTE_Z;
            List<Platzhalterbau> reihe = new ArrayList<>(zeile.getValue());
            reihe.sort(Comparator.comparingInt(
                    bau -> laengsIstX ? bau.bereich().vonX() : bau.bereich().vonZ()));
            for (int i = 1; i < reihe.size(); i++) {
                Quader vorher = reihe.get(i - 1).bereich();
                Quader jetzt = reihe.get(i).bereich();
                int von = (laengsIstX ? vorher.bisX() : vorher.bisZ()) + 1;
                int bis = (laengsIstX ? jetzt.vonX() : jetzt.vonZ()) - 1;
                if (bis < von) {
                    anschluesse++;
                    continue;
                }
                boolean aufDerAchse = von <= mitte + Stadtplan.RADIAL_HALB
                        && bis >= mitte - Stadtplan.RADIAL_HALB;
                if (!aufDerAchse) {
                    hoefe++;
                }
            }
        }
        assertTrue(hoefe >= 8, "so viele Hoefe zwischen den Haeusern: " + hoefe);

        // UND DIE STADT BLEIBT TROTZDEM GESCHLOSSEN: Vier von fuenf Nachbarn teilen
        // ihre Giebelwand. Gemessen waren es 213 von 251. GEZAEHLT, nicht aus paare
        // und hoefe abgeleitet - eine Ableitung haette die Radialgasse geraten.
        assertTrue(anschluesse + hoefe > 0, "es gab Nachbarpaare zu zaehlen");
        assertTrue(anschluesse * 10 >= paare * 8,
                "vier von fuenf Nachbarn muessen anschliessen: " + anschluesse
                        + " von " + paare);

        // 3. HOEHENGEFAELLE: Die Haeuser am Kern sind hoeher als die an der Mauer.
        //    Gemessen wird der Mittelwert je Band, nicht ein einzelnes Haus - ein Haus
        //    traegt auch sein Nestspiel.
        int innenSumme = 0;
        int innenZahl = 0;
        int aussenSumme = 0;
        int aussenZahl = 0;
        for (Platzhalterbau bau : plan.haeuser()) {
            int ring = Integer.MAX_VALUE;
            Quader q = bau.bereich();
            for (int x = q.vonX(); x <= q.bisX(); x++) {
                for (int z = q.vonZ(); z <= q.bisZ(); z++) {
                    ring = Math.min(ring, Stadtplan.ring(x, z));
                }
            }
            if (ring < plan.zeileAussenVon(0)) {
                innenSumme += bau.hoehe();
                innenZahl++;
            } else if (ring >= plan.zeileInnenVon(1)) {
                aussenSumme += bau.hoehe();
                aussenZahl++;
            }
        }
        assertTrue(innenZahl > 20 && aussenZahl > 20,
                "beide Gruppen muessen Haeuser haben: " + innenZahl + "/" + aussenZahl);
        double innen = (double) innenSumme / innenZahl;
        double aussen = (double) aussenSumme / aussenZahl;
        assertTrue(innen > aussen + 1.0,
                "am Kern muss deutlich hoeher gebaut sein als an der Mauer: "
                        + innen + " gegen " + aussen);

        // UND DIE GRUNDHOEHE SELBST FAELLT - gerechnet, nicht gemittelt.
        // DIE ENDEN DES GEFAELLES sind die erste und die letzte Haeuserzeile.
        assertEquals(Stadtplan.HOEHE_INNEN, plan.grundhoehe(plan.zeileInnenVon(0)));
        assertEquals(Stadtplan.HOEHE_AUSSEN, plan.grundhoehe(
                plan.zeileAussenVon(plan.quartiere() - 1) + Stadtplan.ZEILE_TIEFE - 1));
        // UND GEDECKELT: Markt und Mauer liegen ausserhalb und bekommen die Enden.
        assertEquals(Stadtplan.HOEHE_INNEN, plan.grundhoehe(Stadtplan.KERN_RING));
        assertEquals(Stadtplan.HOEHE_AUSSEN, plan.grundhoehe(plan.aussenRing()));
        assertTrue(plan.grundhoehe(plan.zeileInnenVon(0))
                        > plan.grundhoehe(plan.zeileInnenVon(1)),
                "das erste Quartier steht hoeher als das zweite");
    }

    @Test
    @DisplayName("Ein Nest umfasst mehrere Haeuser - und die Nester wechseln")
    void dieNester() {
        Stadtplan plan = Stadtplan.altfurt();

        // DIE NESTGRENZEN liegen je Zeile anders - sonst springt die ganze Stadt an
        // derselben Stelle. Gemessen wird die STELLE DES WECHSELS, nicht die Nummer
        // am Zeilenanfang: Die ist immer 0, denn der Versatz ist kleiner als der
        // Takt. Genau daran ist der erste Entwurf dieser Probe gescheitert - sie war
        // rot, obwohl die Grenzen sich laengst unterschieden.
        Set<Integer> grenzen = new HashSet<>();
        for (int quartier = 0; quartier < plan.quartiere(); quartier++) {
            for (Richtung seite : Richtung.values()) {
                for (boolean innen : new boolean[] {true, false}) {
                    grenzen.add(ersterWechsel(plan, quartier, seite, innen));
                }
            }
        }
        assertTrue(grenzen.size() >= 6,
                "die Nestgrenzen muessen ueber die Zeilen streuen: " + grenzen);

        // UND EIN NEST IST LAENGER ALS EIN HAUS: Ueber NEST_TAKT Bloecke bleibt die
        // Nummer gleich.
        int gleich = 0;
        int gewechselt = 0;
        for (int laengs = 0; laengs < 200; laengs++) {
            if (Stadtplan.nest(0, Richtung.NORD, true, laengs)
                    == Stadtplan.nest(0, Richtung.NORD, true, laengs + 1)) {
                gleich++;
            } else {
                gewechselt++;
            }
        }
        assertEquals(200, gleich + gewechselt, "zweihundert Schritte abgeschritten");
        // EXAKT UND NICHT GERUNDET: Zweihundert Schritte durch siebzehn sind
        // 11,76 - je nach Versatz der Zeile liegen also eilf oder zwoelf Grenzen
        // darin. Eine Zusage auf 200 / NEST_TAKT war darum rot, obwohl der Takt
        // stimmte. Die Zahl der ueberschrittenen Grenzen IST der Nestsprung:
        assertEquals(Stadtplan.nest(0, Richtung.NORD, true, 200)
                        - Stadtplan.nest(0, Richtung.NORD, true, 0), gewechselt,
                "so viele Nestgrenzen liegen zwischen Schritt 0 und 200");
        assertTrue(gleich > gewechselt * 10, "ein Nest ist viel laenger als ein Schritt");

        // UND DER TAKT SELBST, ohne jede Rundung: Genau NEST_TAKT Bloecke weiter
        // steht man im naechsten Nest - an JEDER Stelle, nicht im Mittel.
        for (int laengs = 0; laengs < 50; laengs++) {
            assertEquals(Stadtplan.nest(0, Richtung.NORD, true, laengs) + 1,
                    Stadtplan.nest(0, Richtung.NORD, true, laengs + Stadtplan.NEST_TAKT),
                    "von " + laengs + " aus einen Takt weiter");
        }
    }


    /**
     * Der erste Laengsschritt einer Zeile, an dem das Nest wechselt.
     *
     * <p>Das ist die Lage der ersten NESTGRENZE in dieser Zeile. Sie liegt zwischen 1 und
     * {@link Stadtplan#NEST_TAKT}; faende sich keine, waere der Takt kaputt, und dann
     * soll die Probe daran scheitern und nicht an einer stillen Null.
     */
    private static int ersterWechsel(Stadtplan plan, int quartier, Richtung seite,
            boolean innen) {
        for (int laengs = 1; laengs <= Stadtplan.NEST_TAKT; laengs++) {
            if (Stadtplan.nest(quartier, seite, innen, laengs)
                    != Stadtplan.nest(quartier, seite, innen, laengs - 1)) {
                return laengs;
            }
        }
        throw new AssertionError("in q" + quartier + " " + seite
                + (innen ? " innen" : " aussen") + " wechselt das Nest nie - der Takt"
                + " von " + Stadtplan.NEST_TAKT + " greift nicht");
    }


    @Test
    @DisplayName("Die Gassen biegen aus - und das Haus folgt dem Bogen")
    void dieGassenBiegenAus() {
        Stadtplan plan = Stadtplan.altfurt();

        // WARUM [24.11.2026]: Eine schnurgerade Gasse ist das Zeichen einer am
        // Zeichenbrett gegruendeten Stadt. Altfurt soll gewachsen aussehen, also biegen
        // die Gassen aus - und zwar so, dass BEIDE Leser dasselbe meinen: lageBei gibt
        // die ausgebogenen Bloecke als Gasse aus, und legeZeile setzt das Haus genau um
        // sie zurueck.

        // 1. DIE WELLE SCHLAEGT AUS und kommt zurueck - ueber eine Wellenlaenge sind
        //    beide Enden erreicht.
        Set<Integer> werte = new HashSet<>();
        for (int laengs = 0; laengs < Stadtplan.KRUMM_WELLE; laengs++) {
            werte.add(Stadtplan.versatzBei(plan.zeileInnenVon(0), laengs));
        }
        assertTrue(werte.contains(0), "die Gasse kommt auf ihre Breite zurueck: " + werte);
        assertTrue(werte.contains(Stadtplan.KRUMM_TIEFE),
                "und biegt voll aus: " + werte);

        // 2. SIE IST EIN DREIECK UND KEIN WUERFEL: Von Block zu Block aendert sie sich
        //    um hoechstens einen. Eine Saege waere keine Gasse, sondern ein Schaden.
        for (int laengs = 0; laengs < 4 * Stadtplan.KRUMM_WELLE; laengs++) {
            int hier = Stadtplan.versatzBei(plan.zeileInnenVon(0), laengs);
            int dort = Stadtplan.versatzBei(plan.zeileInnenVon(0), laengs + 1);
            assertTrue(Math.abs(hier - dort) <= 1,
                    "Sprung von " + hier + " auf " + dort + " bei " + laengs);
        }

        // 3. DIE BAENDER HABEN VERSCHIEDENE PHASEN - sonst bogen alle vier Zeilen an
        //    derselben Stelle aus, und das Auge liest daraus wieder ein Raster.
        Set<Integer> phasen = new HashSet<>();
        for (int q = 0; q < plan.quartiere(); q++) {
            phasen.add(Stadtplan.versatzBei(plan.zeileInnenVon(q), 0));
            phasen.add(Stadtplan.versatzBei(plan.zeileAussenVon(q), 0));
        }
        assertTrue(phasen.size() >= 2, "die Baender biegen versetzt aus: " + phasen);

        // 4. UND DIE GASSE IST WIRKLICH VERSCHIEDEN BREIT: Abgeschritten wird die
        //    Kante der ersten Zeile ueber mehrere Wellenlaengen. Gefragt wird lageBei,
        //    also die Antwort, nach der auch gepflastert wird.
        int bandVon = plan.zeileInnenVon(0);
        // BIS ZUM ZEILENENDE und nicht weiter: Dahinter liegt der Eckplatz, und der
        // gehoert weder zur Gasse noch zur Zeile. Der erste Entwurf lief darueber hinaus
        // und meldete, die Gasse frisst elf Bloecke des Bandes - gemessen hatte er den
        // Eckplatz.
        int bisLaengs = Stadtplan.MITTE_Z + Stadtplan.zeilenreichweite(bandVon);
        assertTrue(bisLaengs - (Stadtplan.MITTE_Z + NEBEN_DER_ACHSE)
                        > Stadtplan.KRUMM_WELLE,
                "die Zeile ist laenger als eine Wellenlaenge - sonst sieht dieser Lauf"
                        + " nicht einmal einen ganzen Bogen");
        Set<Integer> kanten = new HashSet<>();
        for (int laengs = Stadtplan.MITTE_Z + NEBEN_DER_ACHSE; laengs <= bisLaengs;
                laengs++) {
            // GEZAEHLT WIRD DER WEG, Fahrbahn und Kante: Der aeusserste Gassenblock
            // ist seit dem 24.11.2026 BORDSTEIN, und wer nur RINGGASSE zaehlt, misst
            // den Bogen um einen Block zu kurz.
            int tief = 0;
            while (tief < Stadtplan.ZEILE_TIEFE
                    && Stadtplan.istWeg(aufRing(plan, bandVon + tief, laengs))) {
                tief++;
            }
            kanten.add(tief);
        }
        assertTrue(kanten.contains(0), "die Gasse kommt auf ihre Breite zurueck: " + kanten);
        assertTrue(kanten.contains(Stadtplan.KRUMM_TIEFE),
                "und ist anderswo um KRUMM_TIEFE weiter: " + kanten);
        assertTrue(kanten.size() > Stadtplan.KRUMM_TIEFE,
                "sie nimmt jeden Wert dazwischen an: " + kanten);
        // UND NIE WEITER ALS DER BOGEN ERLAUBT - sonst frisst sie die Zeile.
        for (int kante : kanten) {
            assertTrue(kante <= Stadtplan.KRUMM_TIEFE,
                    "die Gasse frisst " + kante + " Bloecke des Bandes");
        }

        // 5. KEIN HAUS STEHT IM BOGEN: Das ist die Zusage, die den Bogen von einer
        //    Verengung unterscheidet, und fuer die ganze Stadt haelt sie "Jeder
        //    Hausblock liegt in einer Zeile". Halten KANN sie nur, weil jedes
        //    gerechnete Haus flach genug bleibt, um dem Bogen zu folgen - und weil eine
        //    Vorlage, die den Spielraum nicht laesst, gar nicht erst eingepasst wird.
        assertTrue(Stadtplan.passtInEineZeile(new Vorlage("gerade", 9, 12,
                        Stadtplan.ZEILE_TIEFE - Stadtplan.KRUMM_TIEFE)),
                "so tief darf eine Vorlage sein");
        assertFalse(Stadtplan.passtInEineZeile(new Vorlage("zutief", 9, 12,
                        Stadtplan.ZEILE_TIEFE - Stadtplan.KRUMM_TIEFE + 1)),
                "einen Block tiefer stuende sie im Bogen");
    }


    @Test
    @DisplayName("Die Mauer hat Tuerme - in ungleichen Abstaenden und an jeder Ecke")
    void dieMauertuerme() {
        Stadtplan plan = Stadtplan.altfurt();
        int mauer = plan.mauerVon();
        int aussen = plan.aussenRing();

        // 1. SIE TRETEN VOR DIE MAUER und sind trotzdem Mauer. Ohne diese Zusage waere
        //    ein Turm gar nicht gesetzt: Alles jenseits von aussenRing galt als offenes
        //    Land, und was AUSSEN ist, wird nicht gepflastert.
        int vorDerMauer = 0;
        for (int z = Stadtplan.MITTE_Z - 100; z <= Stadtplan.MITTE_Z + 100; z++) {
            int x = Stadtplan.MITTE_X + aussen + Stadtplan.TURM_VOR;
            if (plan.lageBei(x, z) == Stadtplan.Lage.MAUER) {
                assertTrue(plan.imTurm(x, z),
                        "jenseits der Mauer ist Mauer nur im Turm, bei z " + z);
                vorDerMauer++;
            }
        }
        assertTrue(vorDerMauer >= 3 * Stadtplan.TURM_BREITE,
                "mindestens drei Tuerme treten an der Ostmauer vor: " + vorDerMauer);

        // 2. JE ABSCHNITT GENAU EINER. Die Abschnitte werden von der STADTMITTE aus
        //    gezaehlt - genau so, wie imTurm sie rechnet. Der erste Entwurf dieser Probe
        //    legte seine Fenster an TOR_FREI an und war damit um vierzehn Bloecke
        //    verschoben: Ein Fenster enthielt das Ende eines Turms und den Anfang des
        //    naechsten und meldete zehn Turmbloecke, wo das Programm richtig gerechnet
        //    hatte.
        //
        //    Gezaehlt werden nur Abschnitte, die GANZ zwischen Torhaus und Ecke liegen:
        //    Im Abschnitt am Torhaus faellt der Turm aus, und am Zeilenende uebernimmt
        //    der Eckturm.
        int gezaehlt = 0;
        for (int abschnitt = 1; (abschnitt + 1) * Stadtplan.TURM_TAKT < mauer;
                abschnitt++) {
            int darin = 0;
            for (int laengs = abschnitt * Stadtplan.TURM_TAKT;
                    laengs < (abschnitt + 1) * Stadtplan.TURM_TAKT; laengs++) {
                if (plan.imTurm(Stadtplan.MITTE_X + aussen,
                        Stadtplan.MITTE_Z + laengs)) {
                    darin++;
                }
            }
            assertEquals(Stadtplan.TURM_BREITE, darin,
                    "im Abschnitt " + abschnitt + " steht genau ein Turm");
            gezaehlt++;
        }
        assertTrue(gezaehlt >= 2, "so viele Abschnitte abgeschritten: " + gezaehlt);

        // 3. UND SIE STEHEN UNGLEICH: Die Stellen im Abschnitt unterscheiden sich.
        Set<Integer> stellen = new HashSet<>();
        for (Richtung seite : Richtung.values()) {
            for (int abschnitt = -2; abschnitt <= 2; abschnitt++) {
                stellen.add(Math.floorMod(Stadtplan.turmMitte(seite, abschnitt),
                        Stadtplan.TURM_TAKT));
            }
        }
        assertTrue(stellen.size() >= 5,
                "die Tuerme stehen an verschiedenen Stellen ihrer Abschnitte: "
                        + stellen);
        // UND IMMER MIT ABSTAND ZUM ABSCHNITTSRAND, sonst beruehrten sich zwei Tuerme
        // benachbarter Abschnitte.
        for (Richtung seite : Richtung.values()) {
            for (int abschnitt = -3; abschnitt <= 3; abschnitt++) {
                int mitte = Stadtplan.turmMitte(seite, abschnitt);
                int innerhalb = mitte - abschnitt * Stadtplan.TURM_TAKT;
                assertTrue(innerhalb >= Stadtplan.TURM_BREITE / 2
                                && innerhalb <= Stadtplan.TURM_TAKT
                                        - Stadtplan.TURM_BREITE / 2 - 1,
                        seite + " Abschnitt " + abschnitt + ": der Turm sitzt bei "
                                + innerhalb + " und damit am Rand");
            }
        }

        // 4. DIE VIER ECKEN TRAGEN EINEN TURM, und er ist quadratisch.
        for (int vx : new int[] {-1, 1}) {
            for (int vz : new int[] {-1, 1}) {
                for (int dx = mauer; dx <= aussen + Stadtplan.TURM_VOR; dx++) {
                    for (int dz = mauer; dz <= aussen + Stadtplan.TURM_VOR; dz++) {
                        int x = Stadtplan.MITTE_X + vx * dx;
                        int z = Stadtplan.MITTE_Z + vz * dz;
                        assertTrue(plan.imTurm(x, z),
                                "die Ecke " + vx + "/" + vz + " traegt bei " + dx + "/"
                                        + dz + " keinen Turm");
                        assertEquals(Stadtplan.Lage.MAUER, plan.lageBei(x, z),
                                "und sie ist Mauer");
                    }
                }
            }
        }
        // QUADRATISCH heisst: so breit wie die Mauer dick ist, plus der Vortritt.
        assertEquals(Stadtplan.MAUER_DICKE + Stadtplan.TURM_VOR, Stadtplan.TURM_BREITE,
                "nur dann wird der Eckturm von selbst quadratisch");

        // UND ER STEHT ALLEIN: Vor dem Eckturm liegt ein Stueck gewoehnliche Mauer.
        // Ohne diese Zusage waechst ein Seitenturm mit ihm zu einer doppelt langen
        // Bastion zusammen, und die Ecke sieht aus wie ein Fehler.
        int freiVorDerEcke = 0;
        for (int laengs = mauer - 1; laengs > mauer - Stadtplan.TURM_BREITE; laengs--) {
            if (!plan.imTurm(Stadtplan.MITTE_X + aussen,
                    Stadtplan.MITTE_Z + laengs)) {
                freiVorDerEcke++;
            }
        }
        assertEquals(Stadtplan.TURM_BREITE - 1, freiVorDerEcke,
                "vor dem Eckturm liegt gewoehnliche Mauer");

        // 5. DAS TORHAUS BLEIBT GANZ: Um die Radialachse steht kein Turm.
        for (int z = Stadtplan.MITTE_Z - Stadtplan.TOR_FREI;
                z <= Stadtplan.MITTE_Z + Stadtplan.TOR_FREI; z++) {
            assertFalse(plan.imTurm(Stadtplan.MITTE_X + aussen, z),
                    "bei z " + z + " steht ein Turm im Torhaus");
        }
        for (int x = Stadtplan.MITTE_X - Stadtplan.TOR_FREI;
                x <= Stadtplan.MITTE_X + Stadtplan.TOR_FREI; x++) {
            assertFalse(plan.imTurm(x, Stadtplan.MITTE_Z - aussen),
                    "bei x " + x + " steht ein Turm im Nordtorhaus");
        }

        // 6. DER TURM IST HOEHER ALS DIE MAUER, und seine Brustwehr UMLAEUFT ihn.
        int turmZ = -1;
        for (int z = Stadtplan.MITTE_Z + Stadtplan.TOR_FREI + 1;
                z <= Stadtplan.MITTE_Z + mauer - Stadtplan.TURM_TAKT; z++) {
            if (plan.imTurm(Stadtplan.MITTE_X + aussen, z)
                    && plan.imTurm(Stadtplan.MITTE_X + aussen, z + 1)
                    && plan.imTurm(Stadtplan.MITTE_X + aussen, z - 1)) {
                turmZ = z;
                break;
            }
        }
        assertTrue(turmZ > 0, "einen Turm gefunden, der nicht an seinem Rand liegt");
        int imTurm = plan.mauerhoehe(Stadtplan.MITTE_X + mauer + 1, turmZ);
        assertTrue(imTurm >= Stadtplan.TURM_HOEHE - 1,
                "im Turm ist es " + imTurm + " hoch, die Mauer nur "
                        + Stadtplan.MAUER_HOEHE);
        assertTrue(imTurm > Stadtplan.MAUER_HOEHE, "und hoeher als die Mauer");

        // DIE BRUSTWEHR SITZT AM RAND: An der vordersten Turmkante wechseln die Hoehen
        // ab, in der MITTE des Turmdaches nicht - dort ist Dach und nicht Wehrgang.
        int rand = Stadtplan.MITTE_X + aussen + Stadtplan.TURM_VOR;
        Set<Integer> amRand = new HashSet<>();
        for (int z = turmZ - 1; z <= turmZ + 1; z++) {
            amRand.add(plan.mauerhoehe(rand, z));
        }
        assertEquals(2, amRand.size(), "an der Turmkante wechseln Zinne und Luecke: "
                + amRand);
    }


    @Test
    @DisplayName("Auf der Platzkante stehen Baeume und Brunnen - ungleich verteilt")
    void dieStandorteDerPlatzkante() {
        Stadtplan plan = Stadtplan.altfurt();

        // WARUM [24.11.2026]: Die config.yml versprach auf der Platzkante "Baeume und
        // Brunnen", und gebaut war nichts. Altfurt war durchweg Stein - eine Stadt ohne
        // einen Baum sieht nicht karg aus, sondern unfertig.

        // 1. NUR AUF DER MITTELREIHE EINES KANTENSTREIFENS. Abgeschritten wird der ganze
        //    Platz, Reihe fuer Reihe; gezaehlt wird, auf welchen z-Reihen etwas steht.
        Map<Integer, Integer> jeReihe = new TreeMap<>();
        for (int z = Stadtplan.MITTE_Z - Stadtplan.MARKT_RING;
                z <= Stadtplan.MITTE_Z + Stadtplan.MARKT_RING; z++) {
            for (int x = Stadtplan.MITTE_X - Stadtplan.MARKT_RING;
                    x <= Stadtplan.MITTE_X + Stadtplan.MARKT_RING; x++) {
                if (Stadtplan.kantenbauBei(x, z) != Stadtplan.Kantenbau.KEINER) {
                    jeReihe.merge(z, 1, Integer::sum);
                    assertEquals(Stadtplan.Lage.PLATZKANTE, plan.lageBei(x, z),
                            "bei " + x + "/" + z + " steht etwas, was keine Platzkante"
                                    + " ist");
                }
            }
        }
        assertEquals(2, jeReihe.size(),
                "genau zwei Reihen tragen Standorte - die Mitte der zwei Streifen: "
                        + jeReihe);
        for (int z : jeReihe.keySet()) {
            // DIE MITTE: Darueber und darunter ist auch Platzkante, eine Reihe weiter
            // nicht mehr.
            assertEquals(Stadtplan.Lage.PLATZKANTE, Stadtplan.platzBei(z - 1),
                    "ueber der Reihe " + z);
            assertEquals(Stadtplan.Lage.PLATZKANTE, Stadtplan.platzBei(z + 1),
                    "unter der Reihe " + z);
            assertNotEquals(Stadtplan.Lage.PLATZKANTE, Stadtplan.platzBei(z - 2),
                    "zwei ueber der Reihe " + z + " hoert der Streifen auf");
            assertNotEquals(Stadtplan.Lage.PLATZKANTE, Stadtplan.platzBei(z + 2),
                    "zwei unter der Reihe " + z + " hoert er auf");
        }

        // 2. DER HAUPTWEG BLEIBT FREI: Dort kommt an, wer die Welt betritt.
        for (int z : jeReihe.keySet()) {
            for (int x = Stadtplan.MITTE_X - Stadtplan.KANTE_FREI;
                    x <= Stadtplan.MITTE_X + Stadtplan.KANTE_FREI; x++) {
                assertEquals(Stadtplan.Kantenbau.KEINER, Stadtplan.kantenbauBei(x, z),
                        "bei " + x + "/" + z + " steht etwas im Hauptweg");
            }
        }

        // 3. ES SIND VIELE, UND SIE STEHEN UNGLEICH. Eine Baumreihe in gleichen
        //    Abstaenden ist eine Allee - und genau das Bild, das der Betreiber
        //    "gleichmaessig und repetetativ" genannt hat.
        List<Integer> stellen = new ArrayList<>();
        int z = jeReihe.keySet().iterator().next();
        for (int x = Stadtplan.MITTE_X - Stadtplan.MARKT_RING;
                x <= Stadtplan.MITTE_X + Stadtplan.MARKT_RING; x++) {
            if (Stadtplan.kantenbauBei(x, z) != Stadtplan.Kantenbau.KEINER) {
                stellen.add(x);
            }
        }
        assertTrue(stellen.size() >= 6, "so viele Standorte auf einer Kante: " + stellen);
        Set<Integer> abstaende = new HashSet<>();
        for (int i = 1; i < stellen.size(); i++) {
            int abstand = stellen.get(i) - stellen.get(i - 1);
            abstaende.add(abstand);
            // UND NIE SO ENG, dass zwei Laubkronen ineinander wachsen. Die Schranke
            // ist GERECHNET aus der Kronenweite und keine Zahl: Waechst die Krone, muss
            // der Abstand mitwachsen, und diese Probe ist die Stelle, die es merkt.
            assertTrue(abstand >= 2 * Stadtplan.KRONE_WEITE + 1,
                    "die Standorte " + stellen.get(i - 1) + " und " + stellen.get(i)
                            + " liegen " + abstand + " auseinander, noetig sind "
                            + (2 * Stadtplan.KRONE_WEITE + 1));
        }
        assertTrue(abstaende.size() >= 2,
                "die Abstaende sind verschieden - sonst ist es eine Allee: " + abstaende);
        // UND DER ABSCHNITT TRAEGT DIE KRONE UEBERHAUPT: Waere KANTE_TAKT nicht groesser
        // als zwei Kronenweiten, bliebe kein Spiel fuer die Streuung.
        assertTrue(Stadtplan.KANTE_TAKT > 2 * Stadtplan.KRONE_WEITE,
                "ein Abschnitt von " + Stadtplan.KANTE_TAKT + " traegt eine Krone von "
                        + Stadtplan.KRONE_WEITE);

        // 4. JEDER VIERTE STANDORT IST EIN BRUNNEN, und es gibt beide Arten.
        int baeume = 0;
        int brunnen = 0;
        for (int reihe : jeReihe.keySet()) {
            for (int x = Stadtplan.MITTE_X - Stadtplan.MARKT_RING;
                    x <= Stadtplan.MITTE_X + Stadtplan.MARKT_RING; x++) {
                switch (Stadtplan.kantenbauBei(x, reihe)) {
                    case BAUM -> baeume++;
                    case BRUNNEN -> brunnen++;
                    default -> {
                    }
                }
            }
        }
        assertTrue(baeume > 0, "es stehen Baeume: " + baeume);
        assertTrue(brunnen > 0, "und Brunnen: " + brunnen);
        assertTrue(baeume > brunnen,
                "Baeume sind haeufiger als Brunnen: " + baeume + " gegen " + brunnen);

        // 5. UND AUSSERHALB DER KANTE STEHT NICHTS - auch nicht auf dem Markt, der
        //    dieselben z-Reihen hat wie die Kante ihre x-Stellen.
        assertEquals(Stadtplan.Kantenbau.KEINER,
                Stadtplan.kantenbauBei(Stadtplan.MITTE_X + 20, Stadtplan.MITTE_Z),
                "mitten auf dem Markt");
        assertEquals(Stadtplan.Kantenbau.KEINER,
                Stadtplan.kantenbauBei(Stadtplan.MITTE_X + 20,
                        Stadtplan.MITTE_Z + Stadtplan.KERN_RING),
                "im Kern");
        assertEquals(Stadtplan.Kantenbau.KEINER,
                Stadtplan.kantenbauBei(Stadtplan.MITTE_X + 20,
                        Stadtplan.MITTE_Z + plan.aussenRing()),
                "auf der Mauer");
    }


    @Test
    @DisplayName("Der Kreuzarm fuehrt von der Mitte bis an die Mauer - in allen vier")
    void derKreuzarmFuehrtZumTor() {
        Stadtplan plan = Stadtplan.altfurt();

        // WARUM [24.11.2026]: Der Betreiber sah die Stadt an und sagte, die Wege
        // fuehrten "nur diagonal in die ecken", er wolle "ein plus, sodass die wege
        // direkt zu den toren fuehren". Gemessen war der Grund doppelt: Von Ring 0 bis
        // 51 gab es GAR KEINEN Weg - Markt, Platzkante und Kern waren eine durchgehende
        // Pflasterflaeche -, und alle vier Achsen waren von einem eingetragenen Bau
        // ueberspannt. Man KONNTE nicht gerade hinaus.

        // 1. DURCHGEHEND, BLOCK FUER BLOCK. Abgeschritten wird jede der vier Achsen von
        //    der Marktmitte bis an die Mauer; jeder einzelne Block muss Fahrbahn sein.
        //    Eine Stichprobe haette die Luecke im Kern nicht gefunden - sie lag
        //    zwischen Ring 41 und 51.
        for (int[] richtung : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int bloecke = 0;
            for (int r = 0; r < plan.mauerVon(); r++) {
                int x = Stadtplan.MITTE_X + richtung[0] * r;
                int z = Stadtplan.MITTE_Z + richtung[1] * r;
                assertEquals(Stadtplan.Lage.RADIALGASSE, plan.lageBei(x, z),
                        "auf der Achse " + richtung[0] + "/" + richtung[1]
                                + " muss Ring " + r + " Fahrbahn sein");
                bloecke++;
            }
            assertEquals(plan.mauerVon(), bloecke,
                    "von der Mitte bis an die Mauer abgeschritten");
        }

        // 2. UND ER HOERT AN DER MAUER AUF: Durch sie fuehrt das Torhaus.
        for (int[] richtung : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            assertEquals(Stadtplan.Lage.MAUER,
                    plan.lageBei(Stadtplan.MITTE_X + richtung[0] * plan.mauerVon(),
                            Stadtplan.MITTE_Z + richtung[1] * plan.mauerVon()),
                    "der Arm durchstoesst die Mauer nicht");
        }

        // 3. DIE FAHRBAHN IST SO BREIT WIE DAS TOR, und die Kante liegt daneben.
        //    Gemessen quer ueber den Nordarm, auf halbem Weg zur Mauer.
        int quer = plan.mauerVon() / 2;
        int fahrbahn = 0;
        int kante = 0;
        for (int d = -2 * Stadtplan.KREUZ_HALB; d <= 2 * Stadtplan.KREUZ_HALB; d++) {
            Stadtplan.Lage lage = plan.lageBei(Stadtplan.MITTE_X + d,
                    Stadtplan.MITTE_Z - quer);
            if (lage == Stadtplan.Lage.RADIALGASSE) {
                fahrbahn++;
            } else if (lage == Stadtplan.Lage.BORDSTEIN) {
                kante++;
            }
        }
        assertEquals(2 * Stadtplan.RADIAL_HALB + 1, fahrbahn,
                "die Fahrbahn ist so breit wie der Torhausdurchgang");
        assertEquals(2 * Stadtplan.BORDSTEIN_BREITE, kante,
                "und links und rechts liegt genau eine Kante");

        // 4. DIE KANTE LIEGT AUSSEN, nicht in der Fahrbahn. Abgeschritten von der Achse
        //    nach aussen: zuerst nur Fahrbahn, dann nur Kante, dann etwas anderes.
        for (int d = 0; d <= Stadtplan.RADIAL_HALB; d++) {
            assertEquals(Stadtplan.Lage.RADIALGASSE,
                    plan.lageBei(Stadtplan.MITTE_X + d, Stadtplan.MITTE_Z - quer),
                    "bis RADIAL_HALB ist Fahrbahn, bei " + d);
        }
        for (int d = Stadtplan.RADIAL_HALB + 1; d <= Stadtplan.KREUZ_HALB; d++) {
            assertEquals(Stadtplan.Lage.BORDSTEIN,
                    plan.lageBei(Stadtplan.MITTE_X + d, Stadtplan.MITTE_Z - quer),
                    "dahinter die Kante, bei " + d);
        }
        assertNotEquals(Stadtplan.Lage.BORDSTEIN,
                plan.lageBei(Stadtplan.MITTE_X + Stadtplan.KREUZ_HALB + 1,
                        Stadtplan.MITTE_Z - quer),
                "und dahinter hoert der Arm auf");

        // 5. KEIN HAUS STEHT IM ARM, Kante eingerechnet. Das ist die Zusage, die den
        //    Weg von einer Verengung unterscheidet - und sie muss fuer den GANZEN Arm
        //    gelten, nicht nur fuer die Fahrbahn: Ein Haus auf dem Bordstein waere ein
        //    Haus mitten auf der Strasse.
        for (Platzhalterbau bau : plan.haeuser()) {
            Quader q = bau.bereich();
            for (int x = q.vonX(); x <= q.bisX(); x++) {
                for (int z = q.vonZ(); z <= q.bisZ(); z++) {
                    assertFalse(Stadtplan.imKreuzarm(x, z), bau.schluessel()
                            + " steht bei " + x + "/" + z + " im Kreuzarm");
                }
            }
        }

        // 6. UND DIE DIAGONALE IST KEIN ARM - sonst waere das Kreuz ein Stern.
        for (int d = Stadtplan.KREUZ_HALB + 1; d < plan.mauerVon(); d++) {
            assertFalse(Stadtplan.imKreuzarm(Stadtplan.MITTE_X + d,
                            Stadtplan.MITTE_Z + d),
                    "auf der Diagonale bei " + d + " liegt kein Arm");
        }
    }


    @Test
    @DisplayName("Jede Gasse hat eine Kante - und die Kante folgt den Hausfronten")
    void jedeGasseHatEineKante() {
        Stadtplan plan = Stadtplan.altfurt();

        // WARUM [24.11.2026]: Der Betreiber sah den Bordstein des Kreuzarms und sagte
        // "das strassenmuster mit dem bordstein gefaellt mir, bitte genauso nur schmaler
        // fuer die nebenstrassen und gassen". Die Ringgassen sind GASSE_BREITE breit;
        // mit einer Kante je Seite bleiben drei Bloecke Fahrbahn.

        // 1. QUER DURCH JEDE GASSE: zuerst eine Kante, dann Fahrbahn, dann wieder eine
        //    Kante. Abgeschritten wird die Ostseite auf einer Laengsstelle ohne Bogen,
        //    damit die Nennbreite gemessen wird.
        for (int q = 0; q < plan.quartiere(); q++) {
            int gassenVon = plan.quartierAnfang(q);
            int laengs = ohneBogen(plan, plan.zeileInnenVon(q));
            List<Stadtplan.Lage> quer = new ArrayList<>();
            for (int r = gassenVon; r < plan.zeileInnenVon(q); r++) {
                quer.add(aufRing(plan, r, laengs));
            }
            assertEquals(Stadtplan.GASSE_BREITE, quer.size(),
                    "die Gasse des Quartiers " + q + " ist GASSE_BREITE breit");
            assertEquals(Stadtplan.Lage.BORDSTEIN, quer.get(0),
                    "innen eine Kante: " + quer);
            assertEquals(Stadtplan.Lage.BORDSTEIN, quer.get(quer.size() - 1),
                    "aussen eine Kante: " + quer);
            for (int i = 1; i < quer.size() - 1; i++) {
                assertEquals(Stadtplan.Lage.RINGGASSE, quer.get(i),
                        "dazwischen Fahrbahn, bei " + i + ": " + quer);
            }
        }

        // 2. DIE FAHRBAHN IST SCHMALER ALS DER KREUZARM - "genauso nur schmaler".
        int gassenfahrbahn = Stadtplan.GASSE_BREITE - 2 * Stadtplan.BORDSTEIN_BREITE;
        int armfahrbahn = 2 * Stadtplan.RADIAL_HALB + 1;
        assertEquals(3, gassenfahrbahn, "drei Bloecke Fahrbahn in der Nebenstrasse");
        assertTrue(gassenfahrbahn < armfahrbahn,
                "die Nebenstrasse ist schmaler als der Kreuzarm: " + gassenfahrbahn
                        + " gegen " + armfahrbahn);

        // 3. DIE KANTE FOLGT DEM BOGEN. Wo die Gasse ausbiegt, wandert ihre aeussere
        //    Kante mit - sonst laege sie mitten auf der Fahrbahn. Gemessen wird, auf
        //    welcher Bandtiefe die Kante liegt, ueber eine ganze Wellenlaenge.
        int bandVon = plan.zeileInnenVon(0);
        Set<Integer> kantentiefen = new HashSet<>();
        int bisLaengs = Stadtplan.MITTE_Z + Stadtplan.zeilenreichweite(bandVon);
        for (int laengs = Stadtplan.MITTE_Z + NEBEN_DER_ACHSE; laengs <= bisLaengs;
                laengs++) {
            boolean gefunden = false;
            int tiefe = 0;
            for (int t = 0; t < Stadtplan.ZEILE_TIEFE; t++) {
                if (aufRing(plan, bandVon + t, laengs) == Stadtplan.Lage.BORDSTEIN) {
                    tiefe = t;
                    gefunden = true;
                    break;
                }
            }
            if (gefunden) {
                kantentiefen.add(tiefe);
            }
        }
        assertTrue(kantentiefen.size() >= 2,
                "die aeussere Kante liegt nicht immer auf derselben Bandtiefe: "
                        + kantentiefen);
        for (int tiefe : kantentiefen) {
            assertTrue(tiefe <= Stadtplan.KRUMM_TIEFE,
                    "sie wandert hoechstens um KRUMM_TIEFE: " + tiefe);
        }

        // 4. DIE KANTE LIEGT VOR DEN HAUSFRONTEN. Fuer jedes Haus der inneren Zeile:
        //    der Block unmittelbar vor seiner Front ist Bordstein oder - wo das Haus
        //    weiter zurueckstehen musste - Hofboden. Niemals Fahrbahn: Dann laege die
        //    Kante hinter der Front, also im Haus.
        int gepruefte = 0;
        for (Platzhalterbau bau : plan.haeuser()) {
            if (bau.schluessel().charAt(7) != 'i' || bau.schluessel().charAt(6) != '0') {
                continue;
            }
            // Die NORDzeile: ihre Front zeigt nach Sueden, also zur Mitte.
            if (bau.tuer() != Richtung.SUED) {
                continue;
            }
            Quader b = bau.bereich();
            int vorDerFront = b.bisZ() + 1;
            for (int x = b.vonX(); x <= b.bisX(); x++) {
                Stadtplan.Lage davor = plan.lageBei(x, vorDerFront);
                assertNotEquals(Stadtplan.Lage.RINGGASSE, davor,
                        bau.schluessel() + ": bei " + x + "/" + vorDerFront + " liegt"
                                + " Fahrbahn direkt an der Hausfront - dann ist die"
                                + " Kante hinter der Front, also im Haus");
                gepruefte++;
            }
        }
        assertTrue(gepruefte > 40, "so viele Frontbloecke geprueft: " + gepruefte);

        // 5. DER ECKPLATZ IST KEIN WEG - sonst bekaeme er ringsum eine Kante und saehe
        //    aus wie eine Kreuzung.
        assertFalse(Stadtplan.istWeg(Stadtplan.Lage.ECKPLATZ), "der Eckplatz");
        assertFalse(Stadtplan.istWeg(Stadtplan.Lage.MARKT), "der Markt");
        assertFalse(Stadtplan.istWeg(Stadtplan.Lage.ZEILE), "der Hof");
        assertTrue(Stadtplan.istWeg(Stadtplan.Lage.RINGGASSE), "die Ringgasse");
        assertTrue(Stadtplan.istWeg(Stadtplan.Lage.RADIALGASSE), "der Kreuzarm");
        assertTrue(Stadtplan.istWeg(Stadtplan.Lage.BORDSTEIN), "und seine Kante");
    }


    @Test
    @DisplayName("Die Zeile ist KEINE Reihe: Firstrichtung und Baulinie wechseln")
    void dieZeileIstKeineReihe() {
        Stadtplan plan = Stadtplan.altfurt();

        // WARUM [24.11.2026]: Der Betreiber sah die Stadt zum dritten Mal an und sagte
        // "immernoch sehr gleichmaessig und die haeuser sind einfach nur in einer reihe,
        // statt natuerlich gewachsen". Nester, Hoefe, Hoehengefaelle und krumme Gassen
        // standen da schon - es fehlten die zwei Dinge, die eine REIHE ausmachen:
        //
        //   1. ALLE Firste liefen parallel. Jedes Haus war breiter als tief, also
        //      giebelstaendig, also zeigte jeder Giebel zur Gasse.
        //   2. ALLE Fronten lagen auf einer Linie. Die Welle bog die Gassenkante aus,
        //      aber die Haeuser folgten ihr GEMEINSAM - eine krumme Linie, an der alle
        //      stehen, ist auch eine Reihe.

        List<Platzhalterbau> haeuser = new ArrayList<>();
        for (Platzhalterbau bau : plan.haeuser()) {
            if (bau.schluessel().startsWith("haus-")) {
                haeuser.add(bau);
            }
        }
        assertTrue(haeuser.size() > 200, "eine Stadt: " + haeuser.size());

        // 1. BEIDE FIRSTRICHTUNGEN KOMMEN VOR. Ein Haus, das breiter als tief ist,
        //    laesst sein Dach ueber die TIEFE fallen - sein First laeuft dann PARALLEL
        //    zur Gasse, und das ist ein ganz anderes Bild. Moeglich wurde das erst, als
        //    am 24.11.2026 der Fehler in dachzeigt behoben war: Vorher lagen bei einem
        //    traufstaendigen Bau alle Dachtreppen quer.
        int trauf = 0;
        for (Platzhalterbau bau : haeuser) {
            if (Bauplan.traufstaendig(bau)) {
                trauf++;
            }
        }
        assertTrue(trauf > haeuser.size() / 10,
                "mehr als jedes zehnte Haus steht traufstaendig: " + trauf + " von "
                        + haeuser.size());
        assertTrue(trauf < haeuser.size() / 2,
                "aber die Mehrheit bleibt giebelstaendig - das ist das hansische Bild: "
                        + trauf + " von " + haeuser.size());

        // 2. DIE BAULINIE WECHSELT VON HAUS ZU HAUS. Gezaehlt wird, wie viele Nachbarn
        //    ihre Front teilen; gemessen waren es nach diesem Zug 55 von 217 Paaren.
        //    Vorher waren es ALLE - darum ist diese Zahl die eigentliche Antwort auf
        //    den Befund.
        Map<String, List<Platzhalterbau>> zeilen = new HashMap<>();
        for (Platzhalterbau bau : haeuser) {
            zeilen.computeIfAbsent(bau.schluessel().substring(0, 8),
                    unbenutzt -> new ArrayList<>()).add(bau);
        }
        int paare = 0;
        int gleicheFront = 0;
        Set<Integer> baulinien = new HashSet<>();
        for (Map.Entry<String, List<Platzhalterbau>> zeile : zeilen.entrySet()) {
            boolean laengsIstX = zeile.getKey().charAt(5) == 'n'
                    || zeile.getKey().charAt(5) == 's';
            List<Platzhalterbau> reihe = new ArrayList<>(zeile.getValue());
            reihe.sort(Comparator.comparingInt(
                    bau -> laengsIstX ? bau.bereich().vonX() : bau.bereich().vonZ()));
            for (Platzhalterbau bau : reihe) {
                baulinien.add(baulinie(plan, bau));
            }
            for (int i = 1; i < reihe.size(); i++) {
                paare++;
                if (baulinie(plan, reihe.get(i)) == baulinie(plan, reihe.get(i - 1))) {
                    gleicheFront++;
                }
            }
        }
        assertTrue(paare > 150, "so viele Nachbarpaare: " + paare);
        assertTrue(gleicheFront * 2 < paare,
                "weniger als die Haelfte der Nachbarn teilt die Front - sonst stehen"
                        + " sie wieder in einer Reihe: " + gleicheFront + " von "
                        + paare);
        // UND ES GIBT MEHR ALS ZWEI LINIEN: Zwei waeren zwei Reihen.
        assertTrue(baulinien.size() >= 4,
                "so viele verschiedene Baulinien: " + baulinien);
        assertTrue(baulinien.contains(0),
                "manche Haeuser stehen DIREKT an der Kante: " + baulinien);

        // 2b. DER SPRUNG IST GESTREUT UND NICHT GEDECKELT. Diese Zusage fehlte, und
        //     eine Gegenprobe hat es gezeigt: Ein sprungBei, das immer SPRUNG_TIEFE
        //     gibt, lief durch - weil die Deckelung an der Haustiefe trotzdem
        //     verschiedene Baulinien erzeugt. Dann waere der Sprung aber keine
        //     Entscheidung des Hauses mehr, sondern eine Folge seiner Tiefe, und ein
        //     flaches Haus stuende IMMER hinten. Gefragt wird darum die Rechnung selbst.
        Set<Integer> roheSpruenge = new HashSet<>();
        for (int laengs = 0; laengs < 200; laengs++) {
            roheSpruenge.add(Stadtplan.sprungBei(0, Richtung.NORD, laengs));
        }
        assertTrue(roheSpruenge.contains(0),
                "die meisten Haeuser springen gar nicht: " + roheSpruenge);
        assertTrue(roheSpruenge.size() >= 3,
                "und die uebrigen verschieden weit: " + roheSpruenge);
        for (int sprung : roheSpruenge) {
            assertTrue(sprung >= 0 && sprung <= Stadtplan.SPRUNG_TIEFE,
                    "jeder Sprung liegt in der Spanne: " + sprung);
        }
        // UND DIE MEHRHEIT BLEIBT AN DER KANTE: Eine Zeile, in der jedes Haus springt,
        // hat wieder eine gemeinsame Linie - nur eine andere.
        int ohneSprung = 0;
        for (int laengs = 0; laengs < 300; laengs++) {
            if (Stadtplan.sprungBei(0, Richtung.NORD, laengs) == 0) {
                ohneSprung++;
            }
        }
        assertTrue(ohneSprung * 2 > 300,
                "mehr als die Haelfte springt nicht: " + ohneSprung + " von 300");

        // 3. DIE GASSE BEHAELT TROTZDEM IHRE BREITE. Das ist die tragende Zusage dieses
        //    Zuges: Der Sprung geht in den HAUSQUADER und nicht in die Gassenkante -
        //    sonst wuerde die Gasse bei jedem zurueckgesetzten Haus breiter, und das
        //    sieht nicht nach Vorplatz aus, sondern nach Platzmangel. Gemessen wird die
        //    Fahrbahn quer durch die erste Ringgasse, ueber die ganze Zeilenlaenge.
        int bandVon = plan.zeileInnenVon(0);
        Set<Integer> breiten = new HashSet<>();
        int bisLaengs = Stadtplan.MITTE_Z + Stadtplan.zeilenreichweite(bandVon);
        for (int laengs = Stadtplan.MITTE_Z + NEBEN_DER_ACHSE; laengs <= bisLaengs;
                laengs++) {
            int weg = 0;
            for (int r = plan.quartierAnfang(0); r < bandVon + Stadtplan.ZEILE_TIEFE;
                    r++) {
                if (Stadtplan.istWeg(aufRing(plan, r, laengs))) {
                    weg++;
                } else if (weg > 0) {
                    break;
                }
            }
            breiten.add(weg);
        }
        for (int breite : breiten) {
            assertTrue(breite >= Stadtplan.GASSE_BREITE,
                    "die Gasse wird nie schmaler als ihre Nennbreite: " + breite);
            assertTrue(breite <= Stadtplan.GASSE_BREITE + Stadtplan.KRUMM_TIEFE,
                    "und nie weiter als der Bogen erlaubt - ein zurueckgesetztes Haus"
                            + " weitet sie NICHT: " + breite + " bei einer Nennbreite"
                            + " von " + Stadtplan.GASSE_BREITE + " und einem Bogen von "
                            + Stadtplan.KRUMM_TIEFE);
        }

        // 4. VOR EINEM GESPRUNGENEN HAUS LIEGT SEIN VORPLATZ - Hofboden, keine Gasse.
        //    Daran sieht man, dass es zuruecksteht, und nicht daran, dass die Gasse
        //    dort breiter ist.
        //
        //    BAULINIE IST NICHT SPRUNG, und das hat diese Probe mich gelehrt: Die
        //    Baulinie ist der ganze Abstand der Front von der Bandkante, also
        //    Wellenversatz PLUS Sprung. Ein Haus kann weit hinten stehen, nur weil die
        //    Gasse dort ausbiegt - dann ist vor seiner Tuer Fahrbahn und kein Vorplatz.
        //    Von den 175 Haeusern mit einer Baulinie ueber null haben darum gemessen 50
        //    einen Vorplatz; das sind die mit einem echten Sprung.
        int mitVorplatz = 0;
        for (Platzhalterbau bau : haeuser) {
            if (baulinie(plan, bau) == 0) {
                continue;
            }
            Bauplan.Stelle tuer = Bauplan.tuer(bau);
            if (tuer == null) {
                continue;
            }
            int vorX = tuer.x() + (tuer.zeigt() == Richtung.OST ? 1
                    : tuer.zeigt() == Richtung.WEST ? -1 : 0);
            int vorZ = tuer.z() + (tuer.zeigt() == Richtung.SUED ? 1
                    : tuer.zeigt() == Richtung.NORD ? -1 : 0);
            if (plan.lageBei(vorX, vorZ) == Stadtplan.Lage.ZEILE) {
                mitVorplatz++;
            }
        }
        assertTrue(mitVorplatz >= 40,
                "so viele gesprungene Haeuser haben einen Vorplatz vor der Tuer"
                        + " (gemessen 50 am 24.11.2026): " + mitVorplatz);
    }

    /**
     * Die Bandtiefe der Hausfront - 0 heisst "direkt an der Gassenkante".
     *
     * <p>Gemessen und nicht aus dem Plan geholt: Die Zusage ist, dass die FRONTEN
     * auseinanderliegen, und eine Erwartung, die aus derselben Rechnung kommt wie das
     * Gepruefte, haelt nur, dass eine Zahl sich selbst gleicht.
     */
    private static int baulinie(Stadtplan plan, Platzhalterbau bau) {
        Quader q = bau.bereich();
        int innerster = Integer.MAX_VALUE;
        int aeusserster = 0;
        for (int x = q.vonX(); x <= q.bisX(); x++) {
            for (int z = q.vonZ(); z <= q.bisZ(); z++) {
                int r = Stadtplan.ring(x, z);
                innerster = Math.min(innerster, r);
                aeusserster = Math.max(aeusserster, r);
            }
        }
        for (int quartier = 0; quartier < plan.quartiere(); quartier++) {
            int innen = plan.zeileInnenVon(quartier);
            if (innerster >= innen && aeusserster < innen + Stadtplan.ZEILE_TIEFE) {
                return innerster - innen;
            }
            int aussen = plan.zeileAussenVon(quartier);
            if (innerster >= aussen && aeusserster < aussen + Stadtplan.ZEILE_TIEFE) {
                return aussen + Stadtplan.ZEILE_TIEFE - 1 - aeusserster;
            }
        }
        throw new AssertionError(bau.schluessel() + " liegt in keinem Zeilenband: "
                + q.alsText());
    }

}
