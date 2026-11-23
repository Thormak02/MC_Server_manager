package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /** Die Lage auf Ring {@code ring}, von der Achse weg gemessen. */
    private static Stadtplan.Lage aufRing(Stadtplan plan, int ring) {
        return plan.lageBei(Stadtplan.MITTE_X + ring, Stadtplan.MITTE_Z + NEBEN_DER_ACHSE);
    }

    @Test
    @DisplayName("Die Baender liegen hintereinander und lassen keine Luecke")
    void dieBaender() {
        Stadtplan plan = Stadtplan.altfurt();

        // Die Tafel der Dokumentation, Ring fuer Ring abgeschritten.
        Map<Integer, Stadtplan.Lage> tafel = new TreeMap<>();
        for (int r = NEBEN_DER_ACHSE; r <= 12; r++) {
            tafel.put(r, Stadtplan.Lage.MARKT);
        }
        for (int r = 13; r <= 25; r++) {
            tafel.put(r, Stadtplan.Lage.KERN);
        }
        for (int r = 26; r <= 30; r++) {
            tafel.put(r, Stadtplan.Lage.RINGGASSE);
        }
        for (int r = 31; r <= 52; r++) {
            tafel.put(r, Stadtplan.Lage.ZEILE);
        }
        for (int r = 53; r <= 57; r++) {
            tafel.put(r, Stadtplan.Lage.RINGGASSE);
        }
        for (int r = 58; r <= 79; r++) {
            tafel.put(r, Stadtplan.Lage.ZEILE);
        }
        for (int r = 80; r <= 84; r++) {
            tafel.put(r, Stadtplan.Lage.RINGGASSE);
        }
        for (int r = 85; r <= 87; r++) {
            tafel.put(r, Stadtplan.Lage.MAUER);
        }
        for (int r = 88; r <= 95; r++) {
            tafel.put(r, Stadtplan.Lage.AUSSEN);
        }

        for (Map.Entry<Integer, Stadtplan.Lage> zeile : tafel.entrySet()) {
            assertEquals(zeile.getValue(), aufRing(plan, zeile.getKey()),
                    "Ring " + zeile.getKey());
        }
        // UND DIE ZAHL der gepruefeten Ringe, damit eine geschrumpfte Tafel auffaellt.
        assertEquals(88, tafel.size(), "Ring 8 bis 95 abgeschritten - eine"
                + " geschrumpfte Tafel prueft weniger, ohne rot zu werden");
    }

    @Test
    @DisplayName("Der Markt ist der Platz zwischen den Flankenhaeusern")
    void derMarkt() {
        Stadtplan plan = Stadtplan.altfurt();

        // GEMESSEN an der ausgelieferten Datei: Die Flankenhaeuser stehen bei x 48..58
        // und x 86..96, die Torwand auf z 0, das Bundesratshaus ab z 26.
        assertEquals(Stadtplan.Lage.MARKT, plan.lageBei(60, 1), "Nordwestecke");
        assertEquals(Stadtplan.Lage.MARKT, plan.lageBei(84, 25), "Suedostecke");
        assertEquals(Stadtplan.Lage.MARKT, plan.lageBei(72, 13), "die Ankunft");

        // Einen Block weiter liegt schon der Kern - dort steht Handarbeit.
        assertEquals(Stadtplan.Lage.KERN, plan.lageBei(59, 13), "vor dem Buergeramt");
        assertEquals(Stadtplan.Lage.KERN, plan.lageBei(85, 13), "vor dem Muenzhaus");
        assertEquals(Stadtplan.Lage.KERN, plan.lageBei(72, 0), "die Torwand");
        assertEquals(Stadtplan.Lage.KERN, plan.lageBei(72, 26), "das Bundesratshaus");
    }

    @Test
    @DisplayName("Die Radialgasse durchstoesst die Mauer - sonst hat die Stadt kein Tor")
    void dieRadialgasse() {
        Stadtplan plan = Stadtplan.altfurt();

        // Auf der Achse, durch jedes Band hindurch bis jenseits der Mauer.
        for (int r = 26; r <= 87; r++) {
            assertEquals(Stadtplan.Lage.RADIALGASSE,
                    plan.lageBei(Stadtplan.MITTE_X + r, Stadtplan.MITTE_Z),
                    "Osttor auf Ring " + r);
            assertEquals(Stadtplan.Lage.RADIALGASSE,
                    plan.lageBei(Stadtplan.MITTE_X, Stadtplan.MITTE_Z - r),
                    "Nordtor auf Ring " + r);
        }
        // FUENF BLOECKE BREIT, nicht mehr: Der Block neben der Gasse ist Mauer.
        assertEquals(Stadtplan.Lage.RADIALGASSE,
                plan.lageBei(Stadtplan.MITTE_X + 2, Stadtplan.MITTE_Z - 86), "Rand");
        assertEquals(Stadtplan.Lage.MAUER,
                plan.lageBei(Stadtplan.MITTE_X + 3, Stadtplan.MITTE_Z - 86), "daneben");
        // Und im Kern gilt sie nicht: Dort steht Handarbeit, die dieser Plan nicht kennt.
        assertEquals(Stadtplan.Lage.KERN,
                plan.lageBei(Stadtplan.MITTE_X, Stadtplan.MITTE_Z - 20), "im Kern");
    }

    @Test
    @DisplayName("Die Quartiersecke bleibt ein Platz - dort stiessen zwei Zeilen zusammen")
    void dieEcke() {
        Stadtplan plan = Stadtplan.altfurt();

        // Die Ecke des Rings 41 - beide Abstaende gleich gross.
        assertEquals(Stadtplan.Lage.ECKPLATZ,
                plan.lageBei(Stadtplan.MITTE_X + 41, Stadtplan.MITTE_Z + 41), "die Ecke");
        // DAS INNERE BAND 31..41 reicht laengs bis 31 - ECKE_FREI = 25. Alles
        // darueber ist Platz, UEBER DAS GANZE BAND und nicht nur auf dem innersten
        // Ring: Vorher rechnete die Ecke mit dem Ring der gefragten Stelle, und
        // dieser Block hier hiess ZEILE, obwohl keine Zeile ihn erreicht.
        assertEquals(25, Stadtplan.zeilenreichweite(31), "die Reichweite des Bandes");
        assertEquals(Stadtplan.Lage.ECKPLATZ,
                plan.lageBei(Stadtplan.MITTE_X - 41, Stadtplan.MITTE_Z + 37),
                "noch im Freiraum");
        assertEquals(Stadtplan.Lage.ECKPLATZ,
                plan.lageBei(Stadtplan.MITTE_X - 41, Stadtplan.MITTE_Z + 26),
                "einen Block jenseits der Reichweite");
        // Bis zur Reichweite ist Bauland.
        assertEquals(Stadtplan.Lage.ZEILE,
                plan.lageBei(Stadtplan.MITTE_X - 41, Stadtplan.MITTE_Z + 25),
                "genau auf der Reichweite");
        // DAS AEUSSERE BAND hat seine eigene, GROESSERE Reichweite - 42 - 6 = 36.
        assertEquals(36, Stadtplan.zeilenreichweite(42), "das aeussere Band");
        assertEquals(Stadtplan.Lage.ZEILE,
                plan.lageBei(Stadtplan.MITTE_X + 52, Stadtplan.MITTE_Z + 36),
                "dort reicht die Zeile weiter");
        assertEquals(Stadtplan.Lage.ECKPLATZ,
                plan.lageBei(Stadtplan.MITTE_X + 52, Stadtplan.MITTE_Z + 37),
                "und einen Block weiter nicht mehr");
        // Eine GASSE bekommt keinen Eckplatz - sie laeuft um die Ecke herum.
        assertEquals(Stadtplan.Lage.RINGGASSE,
                plan.lageBei(Stadtplan.MITTE_X + 28, Stadtplan.MITTE_Z + 28),
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
            assertTrue(davor == Stadtplan.Lage.RINGGASSE || davor == Stadtplan.Lage.ZEILE
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
            for (int i = 1; i < reihe.size(); i++) {
                Quader vorher = reihe.get(i - 1).bereich();
                Quader jetzt = reihe.get(i).bereich();
                int lueckeBeginnt = (laengsIstX ? vorher.bisX() : vorher.bisZ()) + 1;
                int lueckeEndet = (laengsIstX ? jetzt.vonX() : jetzt.vonZ()) - 1;
                int breite = lueckeEndet - lueckeBeginnt + 1;
                if (breite > 0) {
                    luecken.add(breite);
                }
            }
            // GENAU EINE LUECKE, und das ist die Radialgasse: Jede andere waere eine
            // Zahnluecke in der Zeile, und genau die hat der Betreiber beklagt.
            assertEquals(1, luecken.size(), zeile.getKey() + " hat Luecken " + luecken);
            assertTrue(luecken.get(0) >= 2 * Stadtplan.RADIAL_HALB + 1,
                    zeile.getKey() + " laesst die Radialgasse nicht frei: " + luecken);
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
        assertEquals(new HashSet<>(Stadtplan.TIEFEN), tiefen, "alle Tiefen");
        assertEquals(new HashSet<>(Stadtplan.HOEHEN), hoehen, "alle Hoehen");
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
        // Mit einem Quartier endet die Stadt schon bei Ring 60.
        Stadtplan klein = new Stadtplan(1);
        assertEquals(53, klein.abschlussgasseVon(), "die Gasse am Wehrgang");
        assertEquals(58, klein.mauerVon(), "die Mauer");
        assertEquals(60, klein.aussenRing(), "der letzte Ring der Stadt");
        assertTrue(klein.haeuser().size() < Stadtplan.altfurt().haeuser().size(),
                "ein Quartier traegt weniger Haeuser als zwei");
    }

    @Test
    @DisplayName("Der Umriss ist die Flaeche, die das Pflaster abgeht")
    void derUmriss() {
        Stadtplan plan = Stadtplan.altfurt();
        Quader umriss = plan.umriss();

        assertEquals(Stadtplan.MITTE_X - 87, umriss.vonX(), "Westrand");
        assertEquals(Stadtplan.MITTE_X + 87, umriss.bisX(), "Ostrand");
        assertEquals(Stadtplan.MITTE_Z - 87, umriss.vonZ(), "Nordrand");
        assertEquals(Stadtplan.MITTE_Z + 87, umriss.bisZ(), "Suedrand");
        // NUR DIE BODENEBENE: Was darueber steht, sind Haeuser und Mauer.
        assertEquals(Stadtplan.BODEN_Y, umriss.vonY(), "Boden");
        assertEquals(Stadtplan.BODEN_Y, umriss.bisY(), "und nur er");
        assertEquals(175L * 175L, umriss.bloecke(), "175 mal 175 Bloecke Pflaster");

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
        // DAS STADTTOR BLEIBT AUSSEN VOR: Dort ist die Mauer offen, und die Hoehe 0.
        // Fuenf Bloecke breit, wie die Radialgasse.
        int offen = 0;
        for (int z = Stadtplan.MITTE_Z - 4; z <= Stadtplan.MITTE_Z + 4; z++) {
            if (plan.mauerhoehe(Stadtplan.MITTE_X + aussen, z) == 0) {
                offen++;
            }
        }
        assertEquals(2 * Stadtplan.RADIAL_HALB + 1, offen,
                "das Osttor ist genau so breit wie die Radialgasse");

        int hoch = 0;
        int tief = 0;
        int zuletzt = -1;
        int wechsel = 0;
        for (int z = Stadtplan.MITTE_Z + 3; z <= Stadtplan.MITTE_Z + 40; z++) {
            int hoehe = plan.mauerhoehe(Stadtplan.MITTE_X + aussen, z);
            assertTrue(hoehe == Stadtplan.MAUER_HOEHE || hoehe == Stadtplan.MAUER_HOEHE - 1,
                    "Mauerhoehe bei z " + z + " ist " + hoehe);
            if (hoehe == Stadtplan.MAUER_HOEHE) {
                hoch++;
            } else {
                tief++;
            }
            if (zuletzt >= 0 && hoehe != zuletzt) {
                wechsel++;
            }
            zuletzt = hoehe;
        }
        assertEquals(38, hoch + tief, "38 Bloecke abgeschritten");
        assertEquals(37, wechsel, "JEDER Nachbar ist anders - eine Zinne, eine Luecke");
        assertTrue(hoch >= 18 && tief >= 18, "etwa halbe halbe: " + hoch + "/" + tief);

        // UND AUF DER ANDEREN ACHSE AUCH: Eine Regel, die nur x fragt, gaebe der Nord-
        // und Suedmauer eine geschlossene Brustwehr.
        wechsel = 0;
        zuletzt = -1;
        for (int x = Stadtplan.MITTE_X + 3; x <= Stadtplan.MITTE_X + 40; x++) {
            int hoehe = plan.mauerhoehe(x, Stadtplan.MITTE_Z - aussen);
            if (zuletzt >= 0 && hoehe != zuletzt) {
                wechsel++;
            }
            zuletzt = hoehe;
        }
        assertEquals(37, wechsel, "auch an der Nordmauer wechselt jeder Block");

        // WO KEINE MAUER IST, IST AUCH KEINE HOEHE.
        assertEquals(0, plan.mauerhoehe(Stadtplan.MITTE_X, Stadtplan.MITTE_Z),
                "die Mitte des Marktes");
        assertEquals(0, plan.mauerhoehe(Stadtplan.MITTE_X + aussen, Stadtplan.MITTE_Z),
                "das Stadttor durchstoesst die Mauer");
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

}
