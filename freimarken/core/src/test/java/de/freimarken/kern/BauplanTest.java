package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Form eines Hansehauses - giebelstaendig, mit Treppengiebel.
 *
 * <p>Die Erwartungen sind GERECHNET und nicht aus {@link Bauplan} geholt: Eine Probe, die
 * ihre Zahl aus der geprueften Funktion nimmt, faengt kein Vertauschen.
 *
 * <p>Das Haus der Proben ist das Buergeramt der ausgelieferten Datei: elf tief, sieben
 * breit, vierzehn hoch, Tuer nach OST. Daraus folgt alles Weitere:
 * {@code (7 - 1) / 2 = 3} Giebelstufen, Dachansatz auf {@code -48 - 3 = -51}.
 */
@DisplayName("Die Form eines Platzhalterbaus")
class BauplanTest {

    /** Das Buergeramt der ausgelieferten Datei. */
    private static Platzhalterbau haus() {
        return haus(Richtung.OST);
    }

    private static Platzhalterbau haus(Richtung tuer) {
        return new Platzhalterbau("buergeramt", new Quader(48, -61, 3, 58, -48, 9),
                tuer, Baustil.BACKSTEIN, Dachart.ZIEGEL);
    }

    /** Dasselbe Haus, aber aus einer VORLAGE - nur die dreht sich. */
    private static Platzhalterbau vorlagenhaus(Richtung tuer) {
        return new Platzhalterbau("buergeramt", new Quader(48, -61, 3, 58, -48, 9),
                tuer, Baustil.BACKSTEIN, Dachart.ZIEGEL, "hansehaus-mittel");
    }

    @Test
    @DisplayName("Giebelstufen und Dachansatz folgen aus der DACHBREITE")
    void giebelUndDachansatz() {
        Platzhalterbau bau = haus();

        assertEquals(7, bau.front(), "sieben breit zur Gasse");
        assertEquals(11, bau.tiefe(), "elf tief");
        assertEquals(14, bau.hoehe());
        // 45 GRAD: (7 - 1) / 2 = 3 Einzuege, je DACHNEIGUNG Ebenen, plus die
        // Firstreihe. Das Dach faellt ueber die DACHBREITE, also die kuerzere der
        // beiden waagerechten Spannen - hier ist das die Front.
        assertEquals(7, Bauplan.dachbreite(bau), "die Front ist die kuerzere");
        assertFalse(Bauplan.traufstaendig(bau), "also giebelstaendig");
        assertEquals(3 * Bauplan.DACHNEIGUNG + 1, Bauplan.giebelstufen(bau));
        assertEquals(4, Bauplan.giebelstufen(bau));
        assertEquals(-52, Bauplan.dachansatz(bau), "-48 minus vier Ebenen Dach");
        // DAS DACH IST EIN DRITTEL DER BAUHOEHE, nicht mehr als die Haelfte
        // [berichtigt 24.11.2026]. Vorher stand hier "ein Hansehaus hat mehr Dach
        // als Wand" - das war aus der Konstante abgeschrieben und nicht gemessen.
        // An einem fremden Fachwerkhaus ausgemessen: fuenf Dachebenen bei neun
        // Bloecken Front und dreizehn Ebenen Bauhoehe, also 38 Prozent. An Mojangs
        // plains_butcher_shop_1: vier Dachebenen bei sieben Bloecken Front.
        assertTrue(Bauplan.giebelstufen(bau) * 4 >= bau.hoehe(),
                "ein Viertel der Bauhoehe muss das Dach mindestens sein");
        assertTrue(Bauplan.giebelstufen(bau) * 2 <= bau.hoehe(),
                "aber nie mehr als die Haelfte - sonst ist es ein Turm mit Hut");

        // DIE PROBE AUF DIE REGEL, nicht auf die Zahl: Ein breiteres Haus hat mehr
        // Stufen. Ohne das waere die Rechnung auch mit einer festen 3 gruen.
        Platzhalterbau rathaus = new Platzhalterbau("bundesratshaus",
                new Quader(64, -61, 26, 80, -46, 38), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.KUPFER);
        assertEquals(17, rathaus.front(), "breit");
        assertEquals(13, rathaus.tiefe(), "und weniger tief");
        // TRAUFSTAENDIG, und das ist der Fund vom 24.11.2026: Vorher fiel das Dach
        // immer ueber die FRONT. Bei diesem Haus waeren das 8 Einzuege und damit
        // 9 Dachebenen bei 11 verfuegbaren - und mit der alten Untergrenze sogar 17,
        // weshalb der Dachansatz unter den Boden rutschte und das Haus NUR aus
        // Giebelstufen bestand: keine Wand, keine Tuer, kein Fenster. Jetzt faellt
        // das Dach ueber die kuerzere Spanne, also ueber die Tiefe.
        assertTrue(Bauplan.traufstaendig(rathaus), "breiter als tief");
        assertEquals(13, Bauplan.dachbreite(rathaus), "die Tiefe faellt");
        assertEquals(7, Bauplan.giebelstufen(rathaus), "(13 - 1) / 2 + 1");
        assertEquals(-53, Bauplan.dachansatz(rathaus));
        assertTrue(Bauplan.dachansatz(rathaus) > rathaus.bereich().vonY(),
                "der Dachansatz muss IM Quader liegen - sonst ist das Haus nur Dach");
        assertEquals(6, Bauplan.dacheinzug(rathaus, Bauplan.giebelstufen(rathaus)),
                "die oberste Ebene traegt den First, also den vollen Einzug");
        // UND BREITER HEISST MEHR STUFEN: dieselbe Hoehe, groessere Dachbreite.
        Platzhalterbau breiter = new Platzhalterbau("breiter",
                new Quader(0, -61, 0, 16, -46, 20), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.KUPFER);
        assertEquals(17, Bauplan.dachbreite(breiter), "jetzt faellt die Front");
        assertTrue(Bauplan.giebelstufen(breiter) > Bauplan.giebelstufen(rathaus),
                "breiteres Dach, mehr Stufen: " + Bauplan.giebelstufen(breiter)
                        + " gegen " + Bauplan.giebelstufen(rathaus));

        // DIE DECKELUNG greift bei einem Bau, der fuer seine Breite zu NIEDRIG ist:
        // Neun breit braeuchte fuenf Dachebenen, acht Hoehe laesst nur drei.
        Platzhalterbau niedrig = new Platzhalterbau("niedrig",
                new Quader(0, -61, 0, 8, -54, 10), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);
        assertEquals(8, niedrig.hoehe(), "acht Ebenen");
        assertEquals(8 - 1 - Bauplan.sockelebenen(niedrig) - 1,
                Bauplan.giebelstufen(niedrig),
                "was neben Boden, Sockel und einer Ebene Wand uebrig bleibt");
        // DIE ZAHL FOLGT AUS DEM SOCKEL dieses Hauses - er streut seit dem
        // 24.11.2026. Bei zwei Ebenen Sockel bleibt eine Dachebene mehr.
        assertEquals(8 - 1 - Bauplan.sockelebenen(niedrig) - 1,
                Bauplan.giebelstufen(niedrig));
        assertTrue(Bauplan.dachansatz(niedrig) > niedrig.bereich().vonY(),
                "auch hier liegt der Dachansatz im Quader");
    }

    @Test
    @DisplayName("Die Drehung folgt aus der Tuerseite - vier Seiten, vier Grade")
    void drehung() {
        // Eine gespeicherte Struktur wird mit der Tuer nach NORDEN gebaut
        // (STRUKTUR_BLICKT). Daraus folgt die Drehung jedes Baus.
        assertEquals(Richtung.NORD, Bauplan.STRUKTUR_BLICKT);
        assertEquals(0, Bauplan.drehungsgrade(Richtung.NORD));
        assertEquals(90, Bauplan.drehungsgrade(Richtung.OST));
        assertEquals(180, Bauplan.drehungsgrade(Richtung.SUED));
        assertEquals(270, Bauplan.drehungsgrade(Richtung.WEST));
        // VIER VERSCHIEDENE - ohne das waere eine Richtung ohne Wirkung.
        assertEquals(4, java.util.Arrays.stream(Richtung.values())
                .map(Bauplan::drehungsgrade).distinct().count());
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.drehungsgrade(null));
    }

    @Test
    @DisplayName("Die EIGENE Datei dreht sich nicht, die VORLAGE schon")
    void dateilage() {
        // DER FEHLER, DEN DAS AUFLOEST [am Server gesehen, 23.11.2026]: Eine gesicherte
        // Datei entstand aus genau diesem Quader - sie steht schon richtig. Wurde sie
        // trotzdem gedreht, war sie 7x14x11 statt 11x14x7 und passte nicht mehr in ihren
        // EIGENEN Quader; vier von neun Haeusern wurden darum gerechnet statt gesetzt.
        for (Richtung tuer : Richtung.values()) {
            Bauplan.Dateilage eigen = Bauplan.dateilage(haus(tuer));

            assertEquals("buergeramt", eigen.datei(),
                    "ohne vorlage: die Datei heisst wie der Bau");
            assertEquals(0, eigen.grad(),
                    tuer + ": eine Datei aus der Weltlage dreht sich NIE");
        }
        // Mit Vorlage: der Name der Vorlage, und die Drehung aus der Tuerseite.
        for (Richtung tuer : Richtung.values()) {
            Bauplan.Dateilage lage = Bauplan.dateilage(vorlagenhaus(tuer));

            assertEquals("hansehaus-mittel", lage.datei());
            assertEquals(Bauplan.drehungsgrade(tuer), lage.grad(),
                    tuer + ": eine Vorlage steht in der Normlage und dreht sich");
        }
        assertThrows(IllegalArgumentException.class, () -> Bauplan.dateilage(null));
    }

    @Test
    @DisplayName("Bei 90 und 270 Grad TAUSCHEN Breite und Tiefe")
    void passtNicht() {
        // DAS IST DER FEHLER, den man sonst erst im Spiel sieht: Eine Struktur von 7
        // breit und 11 tief passt in einen Quader von 7x11 - nach 90 Grad Drehung aber
        // braucht sie 11 breit und 7 tief.
        //
        // Das Haus hier ist 11 (X) x 14 (Y) x 7 (Z), Tuer nach OST - eine VORLAGE wird
        // dafuer um 90 Grad gedreht.
        Platzhalterbau nachOsten = vorlagenhaus(Richtung.OST);
        // Ungedreht 7x14x11 -> gedreht 11x14x7 -> passt genau.
        assertNull(Bauplan.passtNicht(nachOsten, 7, 14, 11));
        // Ungedreht 11x14x7 -> gedreht 7x14x11 -> passt NICHT (11 tief in 7 Tiefe).
        String klage = Bauplan.passtNicht(nachOsten, 11, 14, 7);
        assertNotNull(klage, "ohne Tausch waere dies faelschlich erlaubt");
        assertTrue(klage.contains("90 Grad"), klage);
        assertTrue(klage.contains("buergeramt"), klage);

        // Nach NORDEN dreht sich nichts - dieselben Masse, andere Antwort.
        Platzhalterbau nachNorden = vorlagenhaus(Richtung.NORD);
        assertNull(Bauplan.passtNicht(nachNorden, 11, 14, 7));
        assertNotNull(Bauplan.passtNicht(nachNorden, 7, 14, 11));

        // UND DIE EIGENE DATEI DREHT SICH NIE: Dasselbe Haus ohne Vorlage nimmt genau
        // die Masse seines Quaders an - das war am laufenden Server der Fehler, der vier
        // von neun Haeusern rechnen liess, obwohl ihre Datei dalag.
        Platzhalterbau eigen = haus(Richtung.OST);
        assertNull(Bauplan.passtNicht(eigen, 11, 14, 7),
                "ein gesicherter Quader passt in sich selbst");
        assertNotNull(Bauplan.passtNicht(eigen, 7, 14, 11),
                "und die gedrehte Form passt dann NICHT");

        // Kleiner passt immer, zu hoch nie.
        assertNull(Bauplan.passtNicht(nachOsten, 5, 10, 9));
        assertNotNull(Bauplan.passtNicht(nachOsten, 7, 15, 11));
    }

    @Test
    @DisplayName("Jede Stelle des Quaders hat genau ein Stueck - und keine daneben")
    void jedeStelleHatEinStueck() {
        Platzhalterbau bau = haus();
        Map<Bauplan.Stueck, Integer> gezaehlt = new EnumMap<>(Bauplan.Stueck.class);
        int gefragt = 0;
        for (int x = 48; x <= 58; x++) {
            for (int y = -61; y <= -48; y++) {
                for (int z = 3; z <= 9; z++) {
                    gezaehlt.merge(Bauplan.stueckBei(bau, x, y, z), 1, Integer::sum);
                    gefragt++;
                }
            }
        }
        // GEZAEHLT: Eine Grenze um eins daneben machte die Zahlen unten sonst zu einer
        // Rechnung ueber einen kleineren Quader.
        assertEquals(11 * 14 * 7, gefragt);
        assertEquals(gefragt, gezaehlt.values().stream()
                .mapToInt(Integer::intValue).sum());

        // Der Fussboden ist die ganze Grundflaeche.
        assertEquals(11 * 7, gezaehlt.get(Bauplan.Stueck.BODEN));
        // DER SOCKEL IST SO HOCH, WIE DIESES HAUS IHN HAT [24.11.2026]: Seit der
        // Betreiber "mehr variation bei den haeusern" verlangt hat, streut die
        // Sockelhoehe um SOCKELEBENEN. Die Zusage ist darum nicht mehr "drei Ebenen",
        // sondern "der Ring auf SEINEN Ebenen, minus was aus ihm herausgenommen ist":
        // die Tuer (zwei Ebenen), zwei Laternen und die Fenster der Augenhoehe.
        //
        // Die ZAHL der Fenster im Sockel haengt daran, wie viele Fensterebenen unter
        // seiner Krone liegen - darum gezaehlt und nicht gerechnet.
        int ring = 11 * 7 - 9 * 5;
        int sockel = Bauplan.sockelebenen(bau);
        assertTrue(sockel >= Bauplan.SOCKELEBENEN - Bauplan.SOCKEL_SPIEL
                        && sockel <= Bauplan.SOCKELEBENEN + Bauplan.SOCKEL_SPIEL,
                "die Sockelhoehe liegt in ihrer Spanne: " + sockel);
        int fensterImSockel = 0;
        for (int x = 48; x <= 58; x++) {
            for (int z = 3; z <= 9; z++) {
                for (int y = -61 + 1; y <= -61 + sockel; y++) {
                    if (Bauplan.stueckBei(bau, x, y, z) == Bauplan.Stueck.FENSTER) {
                        fensterImSockel++;
                    }
                }
            }
        }
        assertEquals(ring * sockel - 2 - 2 - fensterImSockel,
                gezaehlt.get(Bauplan.Stueck.SOCKEL),
                "der Sockelring auf " + sockel + " Ebenen, ohne Tuer, Laternen und die "
                        + fensterImSockel + " Fenster darin");
        // Die Tuer ist zwei hoch, die Laterne zweimal einer.
        assertEquals(2, gezaehlt.get(Bauplan.Stueck.TUER));
        assertEquals(2, gezaehlt.get(Bauplan.Stueck.LATERNE));
        // Oben EIN Firstblock je Ebene - und nur die oberste Ebene ist First.
        assertEquals(11, gezaehlt.get(Bauplan.Stueck.FIRST),
                "der First laeuft ueber die ganze Tiefe");
        // Es gibt Fachwerk, Gefach, Fenster, Giebel und Dach - keines davon leer.
        // JEDES Stueck der Aufzaehlung kommt in diesem einen Haus vor - DACHFUSS
        // ist am 24.11.2026 entfallen, weil es bei 45 Grad nie auftreten kann.
        for (Bauplan.Stueck stueck : Bauplan.Stueck.values()) {
            assertTrue(gezaehlt.getOrDefault(stueck, 0) > 0,
                    stueck + " kommt gar nicht vor: " + gezaehlt);
        }

        // DIE VERTEILUNG IST DIE LEHRE AUS DEM ERSTEN VERSUCH [23.11.2026]: Dort war die
        // Wand fast nur Balken und das Fachwerk ein durchlaufendes Fensterband. Gemessen
        // an plains_big_house_1 sind von 340 Bloecken 57 Staemme (ein Sechstel) und 12
        // Glasscheiben (drei von hundert).
        // Gezaehlt wird die FACHWERKZONE und nicht die ganze Wand: Die Wandkrone am
        // Dachansatz ist ein durchgehendes Band, weil sie das Dach traegt - sie ist
        // Konstruktion und kein Muster. Die Zone liegt zwischen dem steinernen
        // Erdgeschoss und der Krone.
        Map<Bauplan.Stueck, Integer> inDerZone = new EnumMap<>(Bauplan.Stueck.class);
        // DIE ZONE BEGINNT UEBER DEM SOCKEL DIESES HAUSES - er streut seit dem
        // 24.11.2026 um SOCKELEBENEN.
        int zoneVon = -61 + Bauplan.sockelebenen(bau) + 1;
        int zoneBis = Bauplan.dachansatz(bau) - 1;
        assertTrue(zoneBis >= zoneVon,
                "ohne Fachwerkzone prueft das Folgende nichts: " + zoneVon + ".."
                        + zoneBis);
        // OHNE DIE BAENDER [berichtigt 24.11.2026]: Mit dem flacheren Dach ist die
        // Zone laenger geworden und enthaelt jetzt ein durchlaufendes Balkenband mitten
        // darin - jedes Geschoss wird oben von einem abgeschlossen. Ein Band ist
        // Konstruktion und kein Muster, genau wie die Wandkrone, die oben schon
        // ausgenommen ist. Gezaehlt werden also nur die Ebenen OHNE Band; erkannt wird
        // ein Band daran, dass auch ZWISCHEN den Staendern ein Balken liegt.
        int gezaehlteEbenen = 0;
        for (int y = zoneVon; y <= zoneBis; y++) {
            if (Bauplan.stueckBei(bau, 51, y, 3) == Bauplan.Stueck.BALKEN) {
                continue;
            }
            gezaehlteEbenen++;
            for (int x = 48; x <= 58; x++) {
                for (int z = 3; z <= 9; z++) {
                    inDerZone.merge(Bauplan.stueckBei(bau, x, y, z), 1, Integer::sum);
                }
            }
        }
        // GEZAEHLT, nicht angenommen: Ohne Ebenen prueft das Folgende nichts.
        assertTrue(gezaehlteEbenen >= 3,
                "so viele bandfreie Fachwerkebenen: " + gezaehlteEbenen);
        int balken = inDerZone.getOrDefault(Bauplan.Stueck.BALKEN, 0);
        int gefach = inDerZone.getOrDefault(Bauplan.Stueck.GEFACH, 0);
        int fenster = inDerZone.getOrDefault(Bauplan.Stueck.FENSTER, 0);
        assertTrue(gefach > balken * 2,
                "Gefach " + gefach + " muss die Staender " + balken + " deutlich"
                        + " ueberwiegen - sonst ist die Wand ein Gitter");
        // KEIN FENSTERBAND - und das wird seit dem 24.11.2026 STRUKTURELL geprueft
        // und nicht an einem Verhaeltnis [Befund dieser Probe]: Als der Balkentakt je
        // Haus zu streuen begann, verschob sich das Verhaeltnis von 1:4 auf 1:3,3 - und
        // eine Schranke, die man dann senkt, hat nie etwas gehalten. Was ein
        // FENSTERBAND ausmacht, ist nicht die Zahl der Fenster, sondern dass zwei
        // nebeneinander sitzen. Genau das wird jetzt gefragt.
        int nachbarn = 0;
        for (int x = 48; x <= 58; x++) {
            for (int y = zoneVon; y <= zoneBis; y++) {
                for (int z = 3; z <= 9; z++) {
                    if (Bauplan.stueckBei(bau, x, y, z) != Bauplan.Stueck.FENSTER) {
                        continue;
                    }
                    if (x < 58 && Bauplan.stueckBei(bau, x + 1, y, z)
                            == Bauplan.Stueck.FENSTER) {
                        nachbarn++;
                    }
                    if (z < 9 && Bauplan.stueckBei(bau, x, y, z + 1)
                            == Bauplan.Stueck.FENSTER) {
                        nachbarn++;
                    }
                }
            }
        }
        assertEquals(0, nachbarn,
                "kein Fenster hat ein Fenster als waagerechten Nachbarn - " + nachbarn
                        + " Paare bilden ein Band, und das sieht nach Kaufhaus aus,"
                        + " nicht nach Hanse");
        // UND DIE WAND BLEIBT UEBERWIEGEND GESCHLOSSEN: gemessen 24 Fenster gegen 80
        // Gefachbloecke am 24.11.2026, also nicht einmal ein Viertel.
        assertTrue(fenster * 3 < gefach,
                "Fenster " + fenster + " gegen Gefach " + gefach);
        assertTrue(fenster > 0, "ganz ohne Fenster ist es ein Speicher");
    }

    @Test
    @DisplayName("Der Treppengiebel steht an der FRONT, das Dach an den Laengsseiten")
    void treppengiebelStehtVorn() {
        // DAS IST DIE GANZE AUSSAGE VON "GIEBELSTAENDIG". Stuende der Giebel an der
        // langen Seite, waere es ein traufstaendiges Haus - und die Silhouette der Gasse
        // waere eine andere.
        Platzhalterbau bau = haus();

        // Dachansatz -52, und das ist die Wandkrone. Die erste Dachebene (-51) liegt
        // noch ohne Einzug darauf - sie ist die Traufe -, und jede weitere zieht sich
        // um genau einen Block ein: 45 Grad.
        assertEquals(0, Bauplan.dacheinzug(bau, 1), "die Traufe zieht sich nicht ein");
        // UND DANN JEDE EBENE UM GENAU EINEN BLOCK - das IST 45 Grad. Vorher zog
        // sich nur jede zweite Ebene ein; daraus entstand die Stufung eines
        // doppelt so steilen Daches.
        assertEquals(1, Bauplan.dacheinzug(bau, 2));
        assertEquals(2, Bauplan.dacheinzug(bau, 3));
        assertEquals(3, Bauplan.dacheinzug(bau, 4),
                "oben der volle Einzug - (7 - 1) / 2");

        // Die erste Dachebene liegt auf -51, denn der Dachansatz (-52) traegt die
        // Wandkrone.
        assertEquals(Bauplan.Stueck.GIEBELSTUFE, Bauplan.stueckBei(bau, 58, -51, 4),
                "die Front traegt die Stufe");
        assertEquals(Bauplan.Stueck.GIEBELSTUFE, Bauplan.stueckBei(bau, 48, -51, 8),
                "die Rueckwand auch - ein Haus hat zwei Giebel");
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(bau, 58, -50, 3),
                "was eingezogen ist, ist Luft");
        // DIE DACHHAUT: je Ebene und Seite GENAU EINE Treppe - bei 45 Grad zieht
        // sich jede Ebene um einen Block ein, also gibt es keinen Fuellblock mehr.
        assertEquals(Bauplan.Stueck.DACH, Bauplan.stueckBei(bau, 53, -51, 3),
                "auf der Traufebene die Treppe an der Kante");
        assertEquals(Bauplan.Stueck.DACH, Bauplan.stueckBei(bau, 53, -50, 4),
                "eine Ebene hoeher einen Block weiter innen");
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(bau, 53, -51, 6),
                "und darunter der Dachboden");

        // Oben: EIN Block, und der ist First - ueber die ganze Tiefe.
        assertEquals(Bauplan.Stueck.FIRST, Bauplan.stueckBei(bau, 58, -48, 6));
        assertEquals(Bauplan.Stueck.FIRST, Bauplan.stueckBei(bau, 53, -48, 6));
        assertEquals(Bauplan.Stueck.FIRST, Bauplan.stueckBei(bau, 48, -48, 6));
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(bau, 53, -48, 5),
                "neben dem First ist nichts mehr");

        // DIE DACHHAUT IST DICHT: Auf jeder Querspalte liegt genau eine oberste Decke.
        for (int z = 3; z <= 9; z++) {
            boolean bedeckt = false;
            for (int y = -48; y >= -54 && !bedeckt; y--) {
                Bauplan.Stueck stueck = Bauplan.stueckBei(bau, 53, y, z);
                bedeckt = stueck == Bauplan.Stueck.DACH
                        || stueck == Bauplan.Stueck.GIEBELSTUFE
                        || stueck == Bauplan.Stueck.FIRST;
            }
            assertTrue(bedeckt, "ueber z " + z + " ist das Dach offen - es regnet hinein");
        }

        // UND DIE TREPPE ZEIGT BERGAUF, zum First: an der einen Traufe nach Sueden, an
        // der anderen nach Norden. Gemessen an Mojangs Daechern.
        assertEquals(Richtung.SUED, Bauplan.dachzeigt(bau, 53, -51, 3));
        assertEquals(Richtung.NORD, Bauplan.dachzeigt(bau, 53, -51, 9));
        assertNull(Bauplan.dachzeigt(bau, 53, -52, 4),
                "die Wandkrone traegt das Dach, ist aber keines");
        assertNull(Bauplan.dachzeigt(bau, 53, -48, 6), "und der First auch nicht");
        assertNull(Bauplan.dachzeigt(bau, 53, -60, 3), "und die Wand erst recht nicht");
    }

    @Test
    @DisplayName("Die Tuer sitzt mittig in der Front, auf der Lauf-Ebene, zwei hoch")
    void dieTuer() {
        Platzhalterbau bau = haus();

        // AM PROBESERVER GEMESSEN [23.11.2026]: Die unterste Quaderebene ERSETZT den
        // Boden - innen und auf der Gasse liegt sie gleich hoch, und wer darauf
        // steht, hat die Fuesse eine Ebene darueber. Vorher begann die Tuer auf
        // vonY + 2; in der Oeffnung stand damit auf der Lauf-Ebene Sockelstein, und
        // wer hinein wollte, musste ueber die Schwelle springen.
        assertEquals(bau.bereich().vonY() + 1, Bauplan.tuerUntersteEbene(bau),
                "auf der Ebene, auf der man laeuft");
        assertEquals(-60, Bauplan.tuerUntersteEbene(bau));
        assertEquals(-59, Bauplan.tuerObersteEbene(bau));
        assertEquals(2, Bauplan.tuerHoehe(bau));
        // OST: die Front ist x = 58, die Mitte quer ist z = 6.
        assertEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(bau, 58, -60, 6),
                "die Lauf-Ebene IST die Tuer und nicht die Schwelle");
        assertEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(bau, 58, -59, 6));
        assertEquals(Bauplan.Stueck.BODEN, Bauplan.stueckBei(bau, 58, -61, 6),
                "darunter der BODEN - ein Tuerblatt braucht eine Unterlage, und die"
                        + " unterste Quaderebene ist sie");
        assertNotEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(bau, 58, -58, 6),
                "darueber nicht mehr");
        assertNotEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(bau, 48, -60, 6),
                "und nicht in der Rueckwand");

        Bauplan.Stelle tuer = Bauplan.tuer(bau);
        assertEquals(new Bauplan.Stelle(58, -60, 6, Richtung.OST), tuer,
                "sie zeigt nach aussen");

        // DIE VIER SEITEN SIND VERSCHIEDEN - eine Richtung ohne Wirkung waere eine
        // Einstellung ohne Wirkung.
        Map<Richtung, Bauplan.Stelle> stellen = new EnumMap<>(Richtung.class);
        for (Richtung richtung : Richtung.values()) {
            stellen.put(richtung, Bauplan.tuer(haus(richtung)));
        }
        assertEquals(4, stellen.values().stream().distinct().count(),
                "vier Seiten, vier Stellen: " + stellen);
        assertEquals(new Bauplan.Stelle(53, -60, 3, Richtung.NORD),
                stellen.get(Richtung.NORD), "Norden ist negatives Z");
    }

    @Test
    @DisplayName("Die Laterne haengt zwei Bloecke neben der Tuer")
    void dieLaterne() {
        Platzhalterbau bau = haus();

        // AUF DER OBERSTEN TUEREBENE: Die Laterne haengt neben dem Tuersturz, und
        // der ist mit der Tuer auf die Lauf-Ebene gewandert.
        assertEquals(-59, Bauplan.tuerObersteEbene(bau), "die Hoehe der Laterne");
        assertEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 58, -59, 4));
        assertEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 58, -59, 8));
        assertNotEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 58, -59, 5),
                "nicht direkt daneben");
        assertNotEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 53, -59, 3),
                "und nicht an der Laengsseite");
        assertNotEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 58, -58, 4),
                "und nicht mehr dort, wo sie vor der Schwellenbehebung hing");
    }

    @Test
    @DisplayName("Das Fachwerk hat seinen Takt: Ecken, jede dritte Spalte, ein Band je Geschoss")
    void dasFachwerk() {
        Platzhalterbau bau = haus();

        // Die vier Ecksaeulen - auf jeder FACHWERKebene. Im steinernen Erdgeschoss
        // (-60 bis -58) gibt es keine hoelzernen Ecken.
        for (int y = -57; y <= -53; y++) {
            assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 48, y, 3),
                    "Ecke auf y " + y);
            assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 58, y, 9),
                    "Ecke auf y " + y);
        }
        assertEquals(Bauplan.Stueck.SOCKEL, Bauplan.stueckBei(bau, 48, -60, 3),
                "die Ecke des Erdgeschosses ist STEIN - ein Sockel hat kein Fachwerk");
        // Die Wandkrone am Dachansatz traegt das oberste Band: Darauf sitzt das Dach.
        for (int x = 49; x <= 57; x++) {
            assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, x, -52, 3),
                    "oberstes Band auf x " + x);
        }
        // STEHENDE BALKEN IM TAKT DIESES HAUSES, von der Ecke gezaehlt. Der Takt
        // streut seit dem 24.11.2026 je Haus - hier standen die festen Stellen x 52 und
        // x 54, und die galten nur fuer einen Takt von vier.
        int takt = Bauplan.balkentakt(bau);
        assertTrue(takt >= Bauplan.BALKENTAKT
                        && takt <= Bauplan.BALKENTAKT + Bauplan.BALKEN_SPIEL,
                "der Takt liegt in seiner Spanne: " + takt);
        int imTakt = 0;
        int dazwischen = 0;
        for (int x = 49; x <= 57; x++) {
            Bauplan.Stueck stueck = Bauplan.stueckBei(bau, x, -57, 3);
            if ((x - 48) % takt == 0) {
                assertEquals(Bauplan.Stueck.BALKEN, stueck,
                        "ein Staender steht bei x " + x + " (Takt " + takt + ")");
                imTakt++;
            } else if (stueck == Bauplan.Stueck.BALKEN) {
                dazwischen++;
            }
        }
        assertTrue(imTakt >= 1, "mindestens ein Staender zwischen den Ecken: " + imTakt);
        assertEquals(0, dazwischen,
                "und keiner daneben - sonst waere der Takt kein Takt");
        assertNotEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 51, -57, 3),
                "und beim Takt vier auch nicht bei drei - das war die alte Fassung");

        // VON DER ECKE GEZAEHLT und nicht vom Weltnullpunkt: Zwei gleich gebaute Haeuser
        // muessen gleich aussehen, auch wenn ihr x um eins verschoben ist.
        // ZWEI HAEUSER AN VERSCHIEDENEN STELLEN SEHEN VERSCHIEDEN AUS [24.11.2026],
        // und das ist der Zug: Hier stand bis heute "derselbe Takt, nur um eins
        // verschoben" - also die Zusage, dass das Fachwerk nur von der Stelle IM Haus
        // abhaengt. Genau daran lagen die Haeuser gleichmaessig: Zwei Haeuser aus
        // demselben Stein waren identisch, auch bei verschiedenen Massen. Seit der
        // Balkentakt je Haus streut, ist das nicht mehr so.
        Platzhalterbau verschoben = new Platzhalterbau("zwilling",
                new Quader(49, -61, 3, 59, -48, 9), Richtung.OST,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);
        Set<Integer> takte = new HashSet<>();
        for (int x = 0; x < 60; x++) {
            takte.add(Bauplan.balkentakt(new Platzhalterbau("takt",
                    new Quader(x, -61, 3, x + 10, -48, 9), Richtung.OST,
                    Baustil.BACKSTEIN, Dachart.ZIEGEL)));
        }
        assertTrue(takte.size() >= 2,
                "der Balkentakt wechselt von Haus zu Haus: " + takte);

        // UND DIE SOCKELHOEHE AUCH. Diese Zusage fehlte, und eine Gegenprobe hat es
        // gezeigt: Ein sockelebenen, das immer SOCKELEBENEN gibt, lief durch - die
        // ganze Stadt haette wieder denselben steinernen Fuss gehabt, und keine Probe
        // haette etwas gesagt.
        Set<Integer> sockel = new HashSet<>();
        for (int x = 0; x < 60; x++) {
            sockel.add(Bauplan.sockelebenen(new Platzhalterbau("sockel",
                    new Quader(x, -61, 3, x + 10, -48, 9), Richtung.OST,
                    Baustil.BACKSTEIN, Dachart.ZIEGEL)));
        }
        assertTrue(sockel.size() >= 2,
                "die Sockelhoehe wechselt von Haus zu Haus: " + sockel);
        for (int hoehe : sockel) {
            assertTrue(hoehe >= Bauplan.SOCKELEBENEN - Bauplan.SOCKEL_SPIEL
                            && hoehe <= Bauplan.SOCKELEBENEN + Bauplan.SOCKEL_SPIEL,
                    "und bleibt in ihrer Spanne: " + hoehe);
            assertTrue(hoehe >= 2,
                    "ein Haus ohne steinernen Fuss gibt es an einer hansischen Gasse"
                            + " nicht: " + hoehe);
        }
        // UND SIE HAENGT NICHT AM BALKENTAKT: Mit denselben Argumenten gestreut haette
        // jedes Haus mit flachem Sockel auch dichtes Fachwerk, und aus zwei Zuegen
        // wuerde einer. Gepruefft an den vier Paarungen, die vorkommen muessen.
        Set<String> paarungen = new HashSet<>();
        for (int x = 0; x < 120; x++) {
            Platzhalterbau einer = new Platzhalterbau("paar",
                    new Quader(x, -61, 3, x + 10, -48, 9), Richtung.OST,
                    Baustil.BACKSTEIN, Dachart.ZIEGEL);
            paarungen.add(Bauplan.sockelebenen(einer) + "/"
                    + Bauplan.balkentakt(einer));
        }
        assertTrue(paarungen.size() >= 4,
                "Sockel und Takt wechseln UNABHAENGIG voneinander: " + paarungen);
        for (int einTakt : takte) {
            // NUR NACH OBEN: Ein engerer Takt gaebe mehr Fenster - siehe BALKEN_SPIEL.
            assertTrue(einTakt >= Bauplan.BALKENTAKT
                            && einTakt <= Bauplan.BALKENTAKT + Bauplan.BALKEN_SPIEL,
                    "und bleibt in seiner Spanne: " + einTakt);
        }

        // DASSELBE HAUS ZWEIMAL GEFRAGT gibt aber dasselbe Bild - das ist die Zusage,
        // die bleiben MUSS: Ein zweites Aufsetzen legt dieselbe Stadt.
        assertEquals(Bauplan.balkentakt(verschoben), Bauplan.balkentakt(verschoben));
        assertEquals(Bauplan.stueckBei(verschoben, 53, -57, 3),
                Bauplan.stueckBei(verschoben, 53, -57, 3));
        assertEquals(Bauplan.sockelebenen(bau), Bauplan.sockelebenen(bau));
    }

    @Test
    @DisplayName("Die Fenster liegen zwischen den Baendern - zwei Ebenen je Geschoss")
    void dieFenster() {
        Platzhalterbau bau = haus();

        // WENIGE UND KLEINE: Ein Fenster steht nur in der Mitte zwischen zwei
        // stehenden Balken, also wo laengs % BALKENTAKT == 2 ist - an der Laengsseite
        // also x 50 und x 56.
        assertEquals(Bauplan.Stueck.FENSTER, Bauplan.stueckBei(bau, 50, -56, 3));
        assertEquals(Bauplan.Stueck.FENSTER, Bauplan.stueckBei(bau, 56, -56, 3));
        assertEquals(Bauplan.Stueck.GEFACH, Bauplan.stueckBei(bau, 51, -56, 3),
                "daneben das Gefach - ein Block Rahmen auf jeder Seite");
        // UNTER DEM FENSTER AUCH - aber WO das Fenster sitzt, haengt am Sockel
        // dieses Hauses, und der streut seit dem 24.11.2026. Gesucht wird darum die
        // Fensterebene und nicht eine feste Zahl genannt.
        boolean fensterGefunden = false;
        int fensterY = 0;
        for (int y = -61 + Bauplan.sockelebenen(bau) + 1; y <= -52; y++) {
            if (Bauplan.stueckBei(bau, 50, y, 3) == Bauplan.Stueck.FENSTER) {
                fensterY = y;
                fensterGefunden = true;
                break;
            }
        }
        assertTrue(fensterGefunden, "ueber dem Sockel sitzt ein Fenster");
        assertEquals(Bauplan.Stueck.GEFACH, Bauplan.stueckBei(bau, 50, fensterY - 1, 3),
                "und unter dem Fenster liegt Gefach");
        assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 50, -52, 3),
                "darueber die Wandkrone - sie gewinnt ueber das Fenster, denn sie traegt"
                        + " das Dach");
        // IM STEINERNEN ERDGESCHOSS auf Augenhoehe: GENAU EINE Ebene. Ein Erdgeschoss
        // ohne Fenster saehe aus wie ein Speicher, zwei Ebenen Fenster im Stein wie
        // eine Ladenfront.
        //
        // GEZAEHLT UND NICHT AN FESTEN HOEHEN GEPRUEFT [24.11.2026]: Die Sockelhoehe
        // streut seit heute, also liegt die Augenhoehe nicht mehr immer auf -59. Die
        // Zusage ist die ZAHL der Fensterebenen im Sockel, nicht ihre Lage.
        int sockel = Bauplan.sockelebenen(bau);
        int fensterebenen = 0;
        for (int y = -61 + 1; y <= -61 + sockel; y++) {
            if (Bauplan.stueckBei(bau, 50, y, 3) == Bauplan.Stueck.FENSTER) {
                fensterebenen++;
            }
        }
        assertEquals(1, fensterebenen,
                "genau eine Fensterebene im steinernen Erdgeschoss von " + sockel
                        + " Ebenen");
        assertEquals(Bauplan.Stueck.SOCKEL, Bauplan.stueckBei(bau, 50, -60, 3),
                "aber nicht auf Bodenhoehe - das waere ein Loch");
    }

    @Test
    @DisplayName("Das Innere ist Luft - es wird MITGESETZT")
    void dasInnereIstLuft() {
        Platzhalterbau bau = haus();

        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(bau, 53, -57, 6));
        assertEquals(Bauplan.Stueck.BODEN, Bauplan.stueckBei(bau, 53, -61, 6),
                "aber der Fussboden liegt");
    }

    @Test
    @DisplayName("Das Schild haengt innen ueber der Tuer und zeigt ins Zimmer")
    void dasSchild() {
        Platzhalterbau bau = haus();

        assertEquals(new Bauplan.Stelle(57, -58, 6, Richtung.WEST), Bauplan.schild(bau),
                "einen Block in den Raum hinein, ueber der Tuer, ins Zimmer zeigend");
    }

    @Test
    @DisplayName("Eine Stelle ausserhalb ist ein Programmfehler und keine Luft")
    void ausserhalbWirft() {
        Platzhalterbau bau = haus();

        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> Bauplan.stueckBei(bau, 59, -61, 3));

        assertTrue(wurf.getMessage().contains("59/-61/3"), wurf.getMessage());
        assertTrue(wurf.getMessage().contains("buergeramt"), wurf.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.stueckBei(bau, 53, -62, 6), "unter dem Boden");
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.stueckBei(bau, 53, -47, 6), "ueber dem First");
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.stueckBei(null, 0, 0, 0));
    }

    @Test
    @DisplayName("Ein Turm von drei Bloecken Front hat EINEN Einzug - und doch ein Dach")
    void schlankerTurm() {
        // Die Tortuerme der ausgelieferten Datei: drei breit, sieben tief, sechzehn hoch.
        Platzhalterbau turm = new Platzhalterbau("forsttor",
                new Quader(70, -61, -7, 72, -46, -1), Richtung.SUED,
                Baustil.FORST, Dachart.KUPFER);

        assertEquals(3, turm.front());
        // EIN Einzug ((3 - 1) / 2), aber DACHNEIGUNG Ebenen dafuer und eine fuer den
        // First. Ein Turm mit nur einer Dachebene haette gar keine Dachflaeche gehabt -
        // genau das war der Fehler, der am laufenden Server gefunden wurde.
        assertEquals(1 * Bauplan.DACHNEIGUNG + 1, Bauplan.giebelstufen(turm));
        assertEquals(-48, Bauplan.dachansatz(turm), "-46 minus zwei Ebenen Dach");
        assertEquals(1, Bauplan.dacheinzug(turm, 3), "oben der volle Einzug");

        // Unter dem First liegt eine echte Dachflaeche - je Seite eine Treppe.
        assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(turm, 70, -48, -4),
                "auf dem Dachansatz liegt die Wandkrone");
        assertEquals(Bauplan.Stueck.DACH, Bauplan.stueckBei(turm, 70, -47, -4));
        assertEquals(Richtung.OST, Bauplan.dachzeigt(turm, 70, -47, -4),
                "die Westtraufe steigt nach Osten, zum First");
        assertEquals(Richtung.WEST, Bauplan.dachzeigt(turm, 72, -47, -4));

        // Oben bleibt genau ein Block uebrig - der First.
        assertEquals(Bauplan.Stueck.FIRST, Bauplan.stueckBei(turm, 71, -46, -4));
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(turm, 70, -46, -4),
                "daneben nichts mehr");
        // Und die Tuer passt noch hinein.
        assertEquals(2, Bauplan.tuerHoehe(turm));
        assertEquals(new Bauplan.Stelle(71, -60, -1, Richtung.SUED), Bauplan.tuer(turm));
    }

    /** Die Breite der Probestruktur, mit der am 23.11.2026 gemessen wurde. */
    private static final int MESS_BREITE = 3;

    /** Die Tiefe derselben Probestruktur - absichtlich NICHT gleich der Breite. */
    private static final int MESS_TIEFE = 5;

    /** Ihre Hoehe. Die Drehung dreht um die Y-Achse, die Hoehe aendert sich also nie. */
    private static final int MESS_HOEHE = 3;

    /**
     * Eine Zeile der Messung vom 23.11.2026 - ein Bau, eine Drehung, drei Markenbloecke.
     *
     * <p>Gemessen am laufenden Server: Dieselbe Probestruktur lag unter vier Namen im
     * Ordner {@code bauten}, einer je Tuerrichtung, und wurde beim Start gesetzt.
     * Danach wurde die Bodenebene jedes Quaders samt sechs Bloecken Rand Feld fuer Feld
     * abgefragt, bis die drei Marken gefunden waren. Die Zahlen hier sind diese Funde.
     *
     * <p>Gold lag in der Datei auf der lokalen Ecke 0/0/0 und liegt damit GENAU auf der
     * Setzstelle - darum ist die gemessene Goldstelle die Erwartung und keine
     * Nachrechnung. Diamant lag am Ende der Breite, Smaragd am Ende der Tiefe; zusammen
     * sind das drei Ecken der Grundflaeche und damit ihre ganze Spanne.
     */
    private record Messung(String bau, Richtung tuer, int grad, int vonX, int vonZ,
            int bisX, int bisZ, int goldX, int goldZ, int diamantX, int diamantZ,
            int smaragdX, int smaragdZ) {

        /** Der Quader des Baus. Die Hoehe ist fuer die Drehung ohne Belang. */
        Quader quader() {
            return new Quader(vonX, -61, vonZ, bisX, -46, bisZ);
        }

        /**
         * Der Bau dazu - MIT Vorlage, denn nur eine Vorlage dreht sich.
         *
         * <p>Gemessen wurde mit einer selbst geschriebenen Datei in der Normlage, also
         * mit genau dem, was eine Vorlage ist. Stil und Dach spielen keine Rolle.
         */
        Platzhalterbau als() {
            return new Platzhalterbau(bau, quader(), tuer, Baustil.BACKSTEIN,
                    Dachart.ZIEGEL, "probestruktur");
        }
    }

    private static final Messung[] GEMESSEN = {
        new Messung("bundesratshaus", Richtung.NORD, 0, 64, 26, 80, 38,
                64, 26, 66, 26, 64, 30),
        new Messung("buergeramt", Richtung.OST, 90, 48, 3, 58, 9,
                52, 3, 52, 5, 48, 3),
        new Messung("forsttor", Richtung.SUED, 180, 70, -7, 72, -1,
                72, -3, 70, -3, 72, -7),
        new Messung("muenzhaus", Richtung.WEST, 270, 86, 3, 96, 9,
                86, 5, 86, 3, 90, 5),
    };

    @Test
    @DisplayName("Die Setzstelle ist die am Server gemessene - in allen vier Drehungen")
    void gemesseneSetzstelle() {
        for (Messung m : GEMESSEN) {
            assertEquals(m.grad(), Bauplan.dateilage(m.als()).grad(),
                    m.bau() + ": gemessen wurde mit der Tuer nach " + m.tuer());
            assertEquals("probestruktur", Bauplan.dateilage(m.als()).datei(),
                    "gemessen wurde mit EINER Datei fuer alle vier Lagen");
            assertEquals(m.goldX(),
                    Bauplan.setzstelleX(m.quader(), m.grad(), MESS_BREITE, MESS_TIEFE),
                    m.bau() + " bei " + m.grad() + " Grad: X der Setzstelle, gemessen am"
                            + " Goldblock");
            assertEquals(m.goldZ(),
                    Bauplan.setzstelleZ(m.quader(), m.grad(), MESS_BREITE, MESS_TIEFE),
                    m.bau() + " bei " + m.grad() + " Grad: Z der Setzstelle, gemessen am"
                            + " Goldblock");
        }
        assertEquals(4, GEMESSEN.length, "vier Drehungen, vier Messungen");
    }

    @Test
    @DisplayName("Die gemessene Flaeche beginnt an der genannten Ecke des Quaders")
    void gemesseneFlaecheSitztInDerEcke() {
        for (Messung m : GEMESSEN) {
            int kleinstesX = Math.min(m.goldX(), Math.min(m.diamantX(), m.smaragdX()));
            int kleinstesZ = Math.min(m.goldZ(), Math.min(m.diamantZ(), m.smaragdZ()));
            int groesstesX = Math.max(m.goldX(), Math.max(m.diamantX(), m.smaragdX()));
            int groesstesZ = Math.max(m.goldZ(), Math.max(m.diamantZ(), m.smaragdZ()));

            // Der ganze Zweck des Ausgleichs: Das Haus steht IM Quader, an der Ecke, die
            // die Datei nennt - und nicht daneben, nicht an der fernen Ecke und nicht
            // mittig.
            assertEquals(m.vonX(), kleinstesX, m.bau() + ": kleinstes gemessenes X");
            assertEquals(m.vonZ(), kleinstesZ, m.bau() + ": kleinstes gemessenes Z");
            assertTrue(groesstesX <= m.bisX(), m.bau() + ": groesstes X " + groesstesX
                    + " ragt ueber " + m.bisX() + " hinaus");
            assertTrue(groesstesZ <= m.bisZ(), m.bau() + ": groesstes Z " + groesstesZ
                    + " ragt ueber " + m.bisZ() + " hinaus");

            // Und der Tausch, den passtNicht annimmt, ist derselbe, der gemessen wurde:
            // bei 90 und 270 Grad liegt die Tiefe nach X.
            boolean quer = m.grad() == 90 || m.grad() == 270;
            assertEquals(quer ? MESS_TIEFE : MESS_BREITE, groesstesX - kleinstesX + 1,
                    m.bau() + ": gemessene Spanne nach X");
            assertEquals(quer ? MESS_BREITE : MESS_TIEFE, groesstesZ - kleinstesZ + 1,
                    m.bau() + ": gemessene Spanne nach Z");
            assertNull(Bauplan.passtNicht(m.als(), MESS_BREITE, MESS_HOEHE, MESS_TIEFE),
                    m.bau() + ": die gemessene Struktur passt in ihren Quader");

            // Scharf wird diese Zusage erst mit einer Struktur, die den Quader GENAU
            // fuellt: Die Probestruktur ist so klein, dass sie auch vertauscht noch
            // hineinpasste. Eine quaderfuellende passt nur in der gemessenen Lage.
            int hatX = m.bisX() - m.vonX() + 1;
            int hatZ = m.bisZ() - m.vonZ() + 1;
            int fuellendeBreite = quer ? hatZ : hatX;
            int fuellendeTiefe = quer ? hatX : hatZ;
            assertNull(Bauplan.passtNicht(m.als(), fuellendeBreite, MESS_HOEHE,
                            fuellendeTiefe),
                    m.bau() + ": " + fuellendeBreite + " breit und " + fuellendeTiefe
                            + " tief fuellt den Quader nach " + m.grad() + " Grad genau");
            assertNotEquals(hatX, hatZ, m.bau() + ": nur ein ungleicher Quader kann den"
                    + " Tausch ueberhaupt zeigen");
            assertNotNull(Bauplan.passtNicht(m.als(), fuellendeTiefe, MESS_HOEHE,
                            fuellendeBreite),
                    m.bau() + ": vertauscht darf dieselbe Struktur NICHT mehr passen");
        }
    }

    @Test
    @DisplayName("Eine Setzstelle ohne Quader, ohne Vierteldrehung oder ohne Masse wirft")
    void setzstelleWirft() {
        Quader q = new Quader(10, -61, 20, 30, -46, 40);

        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.setzstelleX(null, 0, 3, 5), "ohne Quader");
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.setzstelleZ(null, 0, 3, 5), "ohne Quader");
        // 45 Grad faellt sonst stumm auf die ungedrehte Ecke - und das Haus stuende
        // schief, ohne dass es jemand meldet.
        IllegalArgumentException schief = assertThrows(IllegalArgumentException.class,
                () -> Bauplan.setzstelleX(q, 45, 3, 5));
        assertTrue(schief.getMessage().contains("45"), schief.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Bauplan.setzstelleZ(q, 45, 3, 5));
        assertThrows(IllegalArgumentException.class, () -> Bauplan.setzstelleX(q, -90, 3, 5),
                "auch gegen den Uhrzeigersinn gezaehlte Grade nicht");
        assertThrows(IllegalArgumentException.class, () -> Bauplan.setzstelleX(q, 0, 0, 5),
                "eine Struktur ohne Breite gibt es nicht");
        assertThrows(IllegalArgumentException.class, () -> Bauplan.setzstelleZ(q, 0, 3, 0),
                "und ohne Tiefe auch nicht");
    }

    @Test
    @DisplayName("Eine Fensterscheibe kennt die Achse ihrer Wand - und nur dann")
    void dieFensterachse() {
        // DER LETZTE BLOCKFEHLER DER ERSTEN FASSUNG [23.11.2026]: Ohne Blockdaten steht
        // eine Glasscheibe als PFOSTEN mitten im Wandfeld, statt die Fuge zwischen den
        // Gefachbloecken zu fuellen. Dafuer muss der Kern sagen, entlang welcher Achse
        // die Wand laeuft, in der sie sitzt.
        Platzhalterbau bau = haus();

        // Nordwand (z = 3) und Suedwand (z = 9) laufen in x.
        assertEquals(Richtung.OST, Bauplan.fensterachse(bau, 50, -56, 3));
        assertEquals(Richtung.OST, Bauplan.fensterachse(bau, 56, -56, 9));
        // West- und Ostwand laufen in z.
        assertEquals(Richtung.SUED, Bauplan.fensterachse(bau, 48, -56, 5));
        assertEquals(Richtung.SUED, Bauplan.fensterachse(bau, 58, -56, 7));
        // Auch das Fenster im steinernen Erdgeschoss.
        assertEquals(Richtung.OST, Bauplan.fensterachse(bau, 50, -59, 3));

        // UND NUR DORT: Wo kein Fenster ist, gibt es keine Achse. Ohne das wuerde die
        // Naht Gefach- und Balkenbloecke als Scheiben behandeln.
        assertNull(Bauplan.fensterachse(bau, 51, -56, 3), "Gefach");
        assertNull(Bauplan.fensterachse(bau, 48, -56, 3), "Eckpfosten");
        assertNull(Bauplan.fensterachse(bau, 50, -52, 3), "Wandkrone");
        assertNull(Bauplan.fensterachse(bau, 50, -60, 3), "Sockel");
        assertNull(Bauplan.fensterachse(bau, 53, -61, 6), "Fussboden");
        assertNull(Bauplan.fensterachse(bau, 53, -48, 6), "First");
        assertNull(Bauplan.fensterachse(bau, 53, -57, 6), "das Innere");

        // DIE ZWEITE SEITE ist die Gegenrichtung - eine Scheibe, die nur nach einer
        // Seite verbunden ist, endet mitten in der Fuge.
        assertEquals(Richtung.WEST, Bauplan.gegenrichtung(Richtung.OST));
        assertEquals(Richtung.NORD, Bauplan.gegenrichtung(Richtung.SUED));
        assertEquals(Richtung.OST, Bauplan.gegenrichtung(Richtung.WEST));
        assertEquals(Richtung.SUED, Bauplan.gegenrichtung(Richtung.NORD));
        for (Richtung richtung : Richtung.values()) {
            assertEquals(richtung, Bauplan.gegenrichtung(Bauplan.gegenrichtung(richtung)),
                    "zweimal gegen ist wieder hin");
            assertNotEquals(richtung, Bauplan.gegenrichtung(richtung));
        }
        assertThrows(IllegalArgumentException.class, () -> Bauplan.gegenrichtung(null));
    }

    @Test
    @DisplayName("Jede Scheibe des Hauses liegt in einer Wand - keine frei im Raum")
    void jedeScheibeInEinerWand() {
        // Eine Scheibe, fuer die der Kern KEINE Achse nennt, bekaeme von der Naht keine
        // Verbindung und stuende als Pfosten da. Gezaehlt wird darum ueber den ganzen
        // Quader: Fenster und Achse muessen genau dieselbe Menge sein.
        Platzhalterbau bau = haus();
        int fenster = 0;
        int mitAchse = 0;
        for (int x = 48; x <= 58; x++) {
            for (int y = -61; y <= -48; y++) {
                for (int z = 3; z <= 9; z++) {
                    if (Bauplan.stueckBei(bau, x, y, z) == Bauplan.Stueck.FENSTER) {
                        fenster++;
                    }
                    if (Bauplan.fensterachse(bau, x, y, z) != null) {
                        mitAchse++;
                    }
                }
            }
        }
        assertEquals(fenster, mitAchse, "jedes Fenster hat genau eine Achse");
        assertTrue(fenster >= 8, "und es gibt welche: " + fenster);
    }


    @Test
    @DisplayName("Ein liegender Balken kennt seine Achse, ein stehender braucht keine")
    void dieBalkenachse() {
        // AM LAUFENDEN SERVER GEMESSEN [23.11.2026]: Ohne Blockdaten bekommt ein Balken
        // aus stripped_dark_oak_wood axis=y. Beim Eckpfosten ist das richtig, bei der
        // Wandkrone falsch - die geschaelte Laengstextur laeuft dann senkrecht ueber
        // einen waagerechten Balken.
        Platzhalterbau bau = haus();

        // Die Wandkrone am Dachansatz liegt: in der Nordwand entlang x, in der Westwand
        // entlang z.
        assertEquals(Richtung.OST, Bauplan.balkenachse(bau, 52, -52, 3));
        assertEquals(Richtung.OST, Bauplan.balkenachse(bau, 52, -52, 9));
        assertEquals(Richtung.SUED, Bauplan.balkenachse(bau, 48, -52, 6));
        assertEquals(Richtung.SUED, Bauplan.balkenachse(bau, 58, -52, 5));

        // DIE ECKE BLEIBT SENKRECHT: Dort laeuft der Eckpfosten durch, und ein liegender
        // Block in der Ecke zerschnitte ihn.
        assertNull(Bauplan.balkenachse(bau, 48, -52, 3), "Ecke der Wandkrone");
        assertNull(Bauplan.balkenachse(bau, 58, -52, 9), "und die gegenueber");

        // Stehende Balken: keine Achse, denn ihre Vorgabe ist schon richtig.
        assertNull(Bauplan.balkenachse(bau, 48, -57, 3), "Eckpfosten");
        assertNull(Bauplan.balkenachse(bau, 52, -57, 3), "stehender Balken im Takt");

        // Und wo gar kein Balken ist, auch nicht.
        assertNull(Bauplan.balkenachse(bau, 51, -57, 3), "Gefach");
        assertNull(Bauplan.balkenachse(bau, 50, -56, 3), "Fenster");
        assertNull(Bauplan.balkenachse(bau, 48, -60, 3), "Sockel");
        assertNull(Bauplan.balkenachse(bau, 53, -48, 6), "First");
        assertNull(Bauplan.balkenachse(bau, 53, -57, 6), "das Innere");

        // UND DIE DREI ACHSENFRAGEN SIND SICH NIE EINIG: Jede Stelle traegt hoechstens
        // eine Art Blockdatum. Ohne das koennte eine Stelle als Scheibe UND als Balken
        // gesetzt werden, und der zweite Ruf ueberschriebe den ersten.
        for (int x = 48; x <= 58; x++) {
            for (int y = -61; y <= -48; y++) {
                for (int z = 3; z <= 9; z++) {
                    int wieViele = 0;
                    if (Bauplan.dachzeigt(bau, x, y, z) != null) {
                        wieViele++;
                    }
                    if (Bauplan.fensterachse(bau, x, y, z) != null) {
                        wieViele++;
                    }
                    if (Bauplan.balkenachse(bau, x, y, z) != null) {
                        wieViele++;
                    }
                    assertTrue(wieViele <= 1, "zwei Blockdaten auf " + x + "/" + y + "/"
                            + z + ": " + wieViele);
                }
            }
        }
    }


    @Test
    @DisplayName("Jedes dritte Haus hat einen Schornstein - im Dach, nicht auf dem First")
    void derSchornstein() {
        // WARUM [24.11.2026]: Altfurt hat 265 Zeilenhaeuser, und ihre Daecher waren eine
        // Folge gleicher Dreiecke. Ein Schornstein bricht die Firstlinie - der billigste
        // Zug, der eine Dachlandschaft gewachsen aussehen laesst.

        // 1. NICHT JEDES HAUS. Abgeschritten wird eine Reihe von Stellen; gezaehlt wird,
        //    wie viele einen tragen.
        int mit = 0;
        int ohne = 0;
        for (int x = 0; x < 60; x++) {
            Platzhalterbau bau = new Platzhalterbau("probe", new Quader(x, -61, 0,
                    x + 8, -45, 10), Richtung.NORD, Baustil.BACKSTEIN, Dachart.ZIEGEL);
            if (Bauplan.schornstein(bau) == null) {
                ohne++;
            } else {
                mit++;
            }
        }
        assertEquals(60, mit + ohne, "sechzig Stellen abgeschritten");
        assertTrue(mit > 0, "es gibt Schornsteine: " + mit);
        assertTrue(ohne > mit, "aber die Mehrheit hat keinen: " + mit + " von 60");

        // 2. ER LIEGT GANZ IM HAUSQUADER. Das ist die Zusage, die das Setzen umkehrbar
        //    haelt: Wer ein Haus neu setzt, bekaeme sonst den Schornstein des alten dazu.
        //    Der erste Entwurf setzte ihn AUF den First und zwei Ebenen darueber.
        int geprueft = 0;
        for (int x = 0; x < 60; x++) {
            for (int tiefe : new int[] {9, 10, 11}) {
                Platzhalterbau bau = new Platzhalterbau("probe", new Quader(x, -61, 0,
                        x + 8, -45, tiefe - 1), Richtung.NORD, Baustil.BACKSTEIN,
                        Dachart.ZIEGEL);
                Quader schornstein = Bauplan.schornstein(bau);
                if (schornstein == null) {
                    continue;
                }
                assertTrue(bau.bereich().enthaelt(schornstein.vonX(), schornstein.vonY(),
                                schornstein.vonZ()),
                        "der Fuss liegt im Quader: " + schornstein.alsText());
                assertTrue(bau.bereich().enthaelt(schornstein.bisX(), schornstein.bisY(),
                                schornstein.bisZ()),
                        "und die Krone auch: " + schornstein.alsText());
                // EIN BLOCK GRUNDFLAeCHE.
                assertEquals(schornstein.vonX(), schornstein.bisX(), "ein Block breit");
                assertEquals(schornstein.vonZ(), schornstein.bisZ(), "ein Block tief");
                // SEINE KRONE LIEGT AUF FIRSTHOEHE.
                assertEquals(bau.bereich().bisY(), schornstein.bisY(),
                        "die Krone auf Firsthoehe");
                // UND SEIN FUSS UEBER DEM DACHANSATZ - er sitzt im Dach, nicht in der
                // Wand.
                assertTrue(schornstein.vonY() > Bauplan.dachansatz(bau),
                        "der Fuss sitzt im Dach: " + schornstein.vonY() + " gegen "
                                + Bauplan.dachansatz(bau));
                geprueft++;
            }
        }
        assertTrue(geprueft >= 20, "so viele Schornsteine geprueft: " + geprueft);

        // 3. ER STEHT NEBEN DEM FIRST, nicht darauf und nicht an der Traufe.
        Platzhalterbau breit = new Platzhalterbau("breit", new Quader(0, -61, 0,
                8, -45, 10), Richtung.NORD, Baustil.BACKSTEIN, Dachart.ZIEGEL);
        if (Bauplan.schornstein(breit) == null) {
            breit = new Platzhalterbau("breit", new Quader(1, -61, 0,
                    9, -45, 10), Richtung.NORD, Baustil.BACKSTEIN, Dachart.ZIEGEL);
        }
        assertNotNull(Bauplan.schornstein(breit), "eine Stelle mit Schornstein gefunden");
        Quader sch = Bauplan.schornstein(breit);
        // DIE FALLENDE ACHSE ist bei diesem Bau die FRONT (9 breit, 11 tief).
        assertFalse(Bauplan.traufstaendig(breit), "giebelstaendig: tiefer als breit");
        assertEquals(9, Bauplan.dachbreite(breit), "die Front faellt");
        int mitte = (breit.bereich().vonX() + breit.bereich().bisX()) / 2;
        int abstand = Math.abs(sch.vonX() - mitte);
        assertTrue(abstand >= 1, "nicht auf dem First");
        assertTrue(abstand <= (Bauplan.dachbreite(breit) - 1) / 2 - 1,
                "und nicht an der Traufe: Abstand " + abstand);
        // DER ABSTAND IST DIE HOEHE, um die er heraussteht - 45 Grad.
        assertEquals(abstand, sch.bisY() - sch.vonY(),
                "bei 45 Grad faellt das Dach je Block um eine Ebene");

        // 4. EIN SCHMALES DACH TRAEGT KEINEN: Bei Dachbreite drei bleibt zwischen First
        //    und Traufe keine Stelle.
        for (int x = 0; x < 30; x++) {
            Platzhalterbau schmal = new Platzhalterbau("schmal", new Quader(x, -61, 0,
                    x + 2, -50, 8), Richtung.NORD, Baustil.BACKSTEIN, Dachart.ZIEGEL);
            assertEquals(3, Bauplan.dachbreite(schmal), "drei breit");
            assertNull(Bauplan.schornstein(schmal),
                    "ein Dach von drei Bloecken traegt keinen Schornstein, bei x " + x);
        }

        // 5. BEIDE DACHSEITEN UND VERSCHIEDENE STELLEN LAENGS DES FIRSTES - sonst stehen
        //    alle Schornsteine einer Zeile an derselben Stelle, und das Auge liest wieder
        //    ein Raster.
        Set<Integer> seiten = new HashSet<>();
        Set<Integer> laengs = new HashSet<>();
        for (int x = 0; x < 120; x++) {
            Platzhalterbau bau = new Platzhalterbau("probe", new Quader(x, -61, 0,
                    x + 8, -45, 10), Richtung.NORD, Baustil.BACKSTEIN, Dachart.ZIEGEL);
            Quader einer = Bauplan.schornstein(bau);
            if (einer == null) {
                continue;
            }
            int m = (bau.bereich().vonX() + bau.bereich().bisX()) / 2;
            seiten.add(Integer.signum(einer.vonX() - m));
            laengs.add(einer.vonZ() - bau.bereich().vonZ());
        }
        assertEquals(2, seiten.size(),
                "er steht auf BEIDEN Dachhaelften: " + seiten);
        assertTrue(laengs.size() >= 3,
                "und an verschiedenen Stellen laengs des Firstes: " + laengs);

        // 6. OHNE BAU EINE KLAGE.
        assertThrows(IllegalArgumentException.class, () -> Bauplan.schornstein(null));
    }


    @Test
    @DisplayName("Das Haus hat Geschossdecken und einen Treppenschacht")
    void dieGeschossdecken() {
        // WARUM [24.11.2026]: Die 265 Zeilenhaeuser Altfurts waren innen ein leerer
        // Schacht von zwoelf bis zwanzig Ebenen. Von aussen unsichtbar; wer eine Tuer
        // aufmachte, stand in einem Turm.
        // ZWANZIG EBENEN - das hoechste Mass der Stadt, damit mehrere Geschosse
        // uebereinander liegen. Bei siebzehn bleiben nur zwei Decken, und dann prueft
        // der Abstand zwischen ihnen nichts.
        Platzhalterbau bau = new Platzhalterbau("probe",
                new Quader(0, -61, 0, 8, -42, 10), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);
        assertEquals(20, bau.hoehe(), "zwanzig Ebenen");

        // 1. DIE DECKE LIEGT, WO AUSSEN DAS BAND SITZT. Abgeschritten wird eine innere
        //    Spalte von unten nach oben; auf jeder Hoehe muss Decke und Band
        //    uebereinstimmen. Das ist die Zusage, die EINE Hoehenrechnung festhaelt -
        //    eine zweite liefe irgendwann auseinander.
        int innenX = 4;
        int innenZ = 5;
        int decken = 0;
        int luft = 0;
        for (int y = bau.bereich().vonY() + 1; y <= Bauplan.dachansatz(bau); y++) {
            Bauplan.Stueck innen = Bauplan.stueckBei(bau, innenX, y, innenZ);
            // DIE WAND AUF DERSELBEN HOEHE: der liegende Balken, also das Band.
            Bauplan.Stueck wand = Bauplan.stueckBei(bau, bau.bereich().vonX(), y,
                    innenZ);
            if (innen == Bauplan.Stueck.DECKE) {
                decken++;
                assertEquals(Bauplan.Stueck.BALKEN, wand,
                        "auf Hoehe " + y + " liegt innen eine Decke, aussen muss das"
                                + " Band sitzen");
            } else {
                assertEquals(Bauplan.Stueck.LUFT, innen,
                        "innen ist nur Decke oder Luft, auf Hoehe " + y);
                luft++;
            }
        }
        assertTrue(decken >= 3, "so viele Decken: " + decken);
        assertTrue(luft > decken * 2, "zwischen den Decken ist Raum: " + luft
                + " Ebenen Luft gegen " + decken + " Decken");

        // 2. DER ABSTAND IST DIE GESCHOSSHOEHE - gemessen und nicht behauptet.
        List<Integer> hoehen = new ArrayList<>();
        for (int y = bau.bereich().vonY() + 1; y <= Bauplan.dachansatz(bau); y++) {
            if (Bauplan.stueckBei(bau, innenX, y, innenZ) == Bauplan.Stueck.DECKE) {
                hoehen.add(y);
            }
        }
        // DER LETZTE ABSTAND IST KUERZER ERLAUBT: Die oberste Decke ist der Dachboden,
        // und der sitzt auf der WANDKRONE - also dort, wo das Dach ansetzt, und nicht
        // dort, wo der Geschosstakt hinkommt. Gemessen an diesem Haus: Decken auf -54,
        // -50 und -47, also 4 und 3.
        for (int i = 1; i < hoehen.size() - 1; i++) {
            assertEquals(Bauplan.GESCHOSSHOEHE, hoehen.get(i) - hoehen.get(i - 1),
                    "zwischen zwei Wohngeschossen liegt ein Geschoss");
        }
        int letzter = hoehen.get(hoehen.size() - 1) - hoehen.get(hoehen.size() - 2);
        assertTrue(letzter >= 1 && letzter <= Bauplan.GESCHOSSHOEHE,
                "das Dachgeschoss ist hoechstens ein Geschoss hoch: " + letzter);
        // DIE OBERSTE IST DER DACHBODEN, auf der Wandkrone.
        assertEquals(Bauplan.dachansatz(bau), hoehen.get(hoehen.size() - 1),
                "die oberste Decke ist der Dachboden");

        // 3. DER TREPPENSCHACHT GEHT DURCH ALLE DECKEN. Ohne das fuehrte die Leiter
        //    gegen einen Fussboden.
        assertTrue(Bauplan.imSchacht(bau, 1, 1), "der Schacht liegt neben der Ecke");
        assertFalse(Bauplan.imSchacht(bau, innenX, innenZ), "und nicht im Zimmer");
        int sprossen = 0;
        for (int y = bau.bereich().vonY() + 1; y <= Bauplan.dachansatz(bau); y++) {
            assertEquals(Bauplan.Stueck.LEITER, Bauplan.stueckBei(bau, 1, y, 1),
                    "auf Hoehe " + y + " muss im Schacht eine Sprosse sein");
            assertEquals(Richtung.OST, Bauplan.leiterzeigt(bau, 1, y, 1),
                    "und sie zeigt von der Wand weg");
            sprossen++;
        }
        assertEquals(luft + decken, sprossen,
                "die Leiter reicht so weit wie das Innere hoch ist");

        // 4. UNTEN BLEIBT DER FUSSBODEN: Eine Sprosse statt des Bodens waere ein Loch.
        assertEquals(Bauplan.Stueck.BODEN,
                Bauplan.stueckBei(bau, 1, bau.bereich().vonY(), 1),
                "die unterste Ebene bleibt Fussboden");
        // UND OBEN DAS DACH: Ueber dem Dachansatz hat der Giebel das Wort.
        assertNull(Bauplan.leiterzeigt(bau, 1, Bauplan.dachansatz(bau) + 1, 1),
                "ueber dem Dachansatz keine Leiter");

        // 5. EIN ZU KLEINES HAUS BEKOMMT KEINEN SCHACHT: Bei drei Bloecken Aussenmass
        //    waere die Spalte neben der Ecke die einzige innere.
        Platzhalterbau winzig = new Platzhalterbau("winzig",
                new Quader(0, -61, 0, 2, -50, 8), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);
        assertEquals(3, winzig.front(), "drei breit");
        assertFalse(Bauplan.imSchacht(winzig, 1, 1),
                "ein Haus von drei Bloecken Breite bekommt keinen Schacht");

        // 6. OHNE BAU EINE KLAGE.
        assertThrows(IllegalArgumentException.class, () -> Bauplan.imSchacht(null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.leiterzeigt(null, 0, 0, 0));
        // UND AUSSERHALB DES BAUS keine Leiter, aber auch kein Wurf - gefragt wird von
        // einer Schleife, die den Quader abschreitet, und die kommt nie daneben; eine
        // Probe aber schon.
        assertNull(Bauplan.leiterzeigt(bau, 100, -50, 100), "ausserhalb");
    }


    @Test
    @DisplayName("JEDE Dachtreppe zeigt bergauf - auch am traufstaendigen Bau")
    void jedeDachtreppeZeigtBergauf() {
        // WARUM DIESE PROBE [24.11.2026]: Die Richtung der Dachtreppen wurde bis heute
        // an DREI Stellen einer einzigen Spaltenlinie eines giebelstaendigen Hauses
        // geprueft. Gemessen am Bundesratshaus (17 breit, 13 tief, also TRAUFstaendig)
        // lagen ALLE 179 Dachtreppen quer statt bergauf - keine einzige richtig, und
        // keine Probe sagte etwas. Die Ursache: imGiebel waehlte die fallende Achse
        // ueber traufstaendig(), dachzeigt() fragte nur die Tuerseite. Zwei Leser
        // derselben Sache, verschieden gerechnet.
        //
        // Diese Probe schreitet den GANZEN Quader ab und prueft die Zusage selbst:
        // Geht man von einer Dachstelle einen Block in die genannte Richtung UND eine
        // Ebene hoeher, muss dort wieder Dachhaut liegen - Dach, First, Giebelstufe
        // oder Schornstein. Das ist, was "bergauf" bedeutet.
        for (Platzhalterbau bau : List.of(
                // giebelstaendig, Tuer NORD - das gewoehnliche Zeilenhaus
                new Platzhalterbau("zeilenhaus", new Quader(0, -61, 0, 8, -45, 10),
                        Richtung.NORD, Baustil.BACKSTEIN, Dachart.ZIEGEL),
                // giebelstaendig, Tuer OST - die andere Achse
                new Platzhalterbau("zeileost", new Quader(0, -61, 0, 10, -45, 8),
                        Richtung.OST, Baustil.FACHWERK, Dachart.KUPFER),
                // TRAUFSTAENDIG, Tuer NORD - das Bundesratshaus
                new Platzhalterbau("bundesratshaus",
                        new Quader(64, -61, 26, 80, -46, 38), Richtung.NORD,
                        Baustil.BACKSTEIN, Dachart.KUPFER),
                // TRAUFSTAENDIG, Tuer OST - damit beide Achsen traufstaendig dran sind
                new Platzhalterbau("breitost", new Quader(0, -61, 0, 12, -46, 28),
                        Richtung.OST, Baustil.SCHIEFER, Dachart.ZIEGEL))) {
            Quader q = bau.bereich();
            int dachstellen = 0;
            for (int x = q.vonX(); x <= q.bisX(); x++) {
                for (int z = q.vonZ(); z <= q.bisZ(); z++) {
                    for (int y = q.vonY(); y <= q.bisY(); y++) {
                        if (Bauplan.stueckBei(bau, x, y, z) != Bauplan.Stueck.DACH) {
                            continue;
                        }
                        dachstellen++;
                        Richtung zeigt = Bauplan.dachzeigt(bau, x, y, z);
                        assertNotNull(zeigt, bau.schluessel() + ": die Dachstelle " + x
                                + "/" + y + "/" + z + " hat keine Richtung");
                        int nx = x + (zeigt == Richtung.OST ? 1
                                : zeigt == Richtung.WEST ? -1 : 0);
                        int nz = z + (zeigt == Richtung.SUED ? 1
                                : zeigt == Richtung.NORD ? -1 : 0);
                        assertTrue(bau.enthaelt(nx, y + 1, nz), bau.schluessel()
                                + ": die Treppe bei " + x + "/" + y + "/" + z
                                + " zeigt " + zeigt + " und damit aus dem Bau hinaus");
                        Bauplan.Stueck bergauf = Bauplan.stueckBei(bau, nx, y + 1, nz);
                        assertTrue(bergauf == Bauplan.Stueck.DACH
                                        || bergauf == Bauplan.Stueck.FIRST
                                        || bergauf == Bauplan.Stueck.GIEBELSTUFE
                                        || bergauf == Bauplan.Stueck.SCHORNSTEIN,
                                bau.schluessel() + ": die Treppe bei " + x + "/" + y
                                        + "/" + z + " zeigt " + zeigt + ", aber bergauf"
                                        + " liegt " + bergauf + " - sie liegt quer");
                    }
                }
            }
            // GEZAEHLT, damit ein Bau ohne Dachflaeche nicht stillschweigend gruen ist.
            assertTrue(dachstellen > 40, bau.schluessel() + " hat nur " + dachstellen
                    + " Dachstellen - diese Probe haette fast nichts gemessen");
        }
    }

    @Test
    @DisplayName("Die fallende Achse ist bei beiden Lesern dieselbe")
    void dieFallendeAchseIstEine() {
        // DIE ZUSAGE HINTER DEM FUND vom 24.11.2026: imGiebel und dachzeigt muessen
        // dieselbe Achse meinen. Gepruefter Beweis, ohne die private Methode zu
        // sehen: Die Dachflaeche eines traufstaendigen Baus muss ueber die TIEFE
        // fallen - also laufen die Treppenreihen laengs der FRONT, und die Richtungen
        // sind NORD/SUED bei einer Tuer nach NORD.
        Platzhalterbau trauf = new Platzhalterbau("bundesratshaus",
                new Quader(64, -61, 26, 80, -46, 38), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.KUPFER);
        assertTrue(Bauplan.traufstaendig(trauf), "breiter als tief");
        assertEquals(13, Bauplan.dachbreite(trauf), "die Tiefe faellt");

        Set<Richtung> richtungen = new HashSet<>();
        Quader q = trauf.bereich();
        for (int x = q.vonX(); x <= q.bisX(); x++) {
            for (int z = q.vonZ(); z <= q.bisZ(); z++) {
                for (int y = q.vonY(); y <= q.bisY(); y++) {
                    Richtung zeigt = Bauplan.dachzeigt(trauf, x, y, z);
                    if (zeigt != null) {
                        richtungen.add(zeigt);
                    }
                }
            }
        }
        // FAELLT DAS DACH UEBER DIE TIEFE (z), zeigen die Treppen in z - also NORD und
        // SUED. Vor dem 24.11.2026 standen hier OST und WEST, quer zum Gefaelle.
        assertEquals(Set.of(Richtung.NORD, Richtung.SUED), richtungen,
                "ein traufstaendiger Bau mit Tuer nach Norden laesst sein Dach in z"
                        + " fallen: " + richtungen);

        // UND BEIM GIEBELSTAENDIGEN umgekehrt - sonst haette die Probe oben auch mit
        // einer festen Antwort gehalten.
        Platzhalterbau giebel = new Platzhalterbau("zeilenhaus",
                new Quader(0, -61, 0, 8, -45, 10), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);
        assertFalse(Bauplan.traufstaendig(giebel), "tiefer als breit");
        Set<Richtung> andere = new HashSet<>();
        Quader g = giebel.bereich();
        for (int x = g.vonX(); x <= g.bisX(); x++) {
            for (int z = g.vonZ(); z <= g.bisZ(); z++) {
                for (int y = g.vonY(); y <= g.bisY(); y++) {
                    Richtung zeigt = Bauplan.dachzeigt(giebel, x, y, z);
                    if (zeigt != null) {
                        andere.add(zeigt);
                    }
                }
            }
        }
        assertEquals(Set.of(Richtung.OST, Richtung.WEST), andere,
                "ein giebelstaendiger Bau mit Tuer nach Norden laesst sein Dach in x"
                        + " fallen: " + andere);
    }


    @Test
    @DisplayName("Ein Bau ueber dem Kreuzarm bekommt eine Laube - mit Pfeilern")
    void dieLaube() {
        // WARUM [24.11.2026]: Die Stadtkirche und das Bundesratshaus stehen MITTIG auf
        // ihrem Platz und ueberspannten damit den Nord- und den Suedarm vollstaendig -
        // je 99 Bloecke. Ein Weg, der um sie herum knickt, ist kein Weg zum Tor. Der
        // Arm geht darum durch sie HINDURCH, und eine Laube hat jede Hansestadt.
        //
        // Das Bundesratshaus aus der ausgelieferten config.yml, auf die Mitte 0/0
        // bezogen: x -16..16, z 41..51.
        Platzhalterbau rathaus = new Platzhalterbau("bundesratshaus",
                new Quader(-16, -61, 41, 16, -46, 51), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.KUPFER);
        assertTrue(Bauplan.ueberspanntKreuzarm(rathaus),
                "es ueberspannt den Suedarm ganz");

        int fuss = rathaus.bereich().vonY();
        // DIE FAHRBAHN IST OFFEN, ueber die ganze Hoehe der Laube und die ganze Tiefe
        // des Baus.
        int luft = 0;
        for (int x = -Stadtplan.RADIAL_HALB; x <= Stadtplan.RADIAL_HALB; x++) {
            for (int z = rathaus.bereich().vonZ(); z <= rathaus.bereich().bisZ(); z++) {
                for (int y = fuss + 1; y <= fuss + Bauplan.LAUBE_HOEHE; y++) {
                    assertEquals(Bauplan.Stueck.LUFT,
                            Bauplan.stueckBei(rathaus, x, y, z),
                            "in der Laube bei " + x + "/" + y + "/" + z);
                    luft++;
                }
            }
        }
        assertEquals((2 * Stadtplan.RADIAL_HALB + 1) * rathaus.tiefe()
                        * Bauplan.LAUBE_HOEHE, luft,
                "die ganze Fahrbahn ist offen");

        // DER FUSSBODEN BLEIBT: Man laeuft durch die Laube, nicht in einen Graben.
        assertEquals(Bauplan.Stueck.BODEN,
                Bauplan.stueckBei(rathaus, 0, fuss, rathaus.bereich().vonZ() + 2),
                "der Boden der Laube");

        // UND DIE WAENDE STEHEN: Auf der KANTE des Arms ist Mauerwerk, nicht Luft.
        // Ohne sie oeffnet sich der Durchgang nach beiden Seiten in den hohlen
        // Innenraum - man laeuft durch die Laube und steht im Gebaeude, ohne eine Tuer
        // genommen zu haben. Genau das hat diese Probe gefunden.
        for (int seite : new int[] {-1, 1}) {
            int x = seite * (Stadtplan.RADIAL_HALB + 1);
            assertEquals(Stadtplan.Lage.BORDSTEIN,
                    Stadtplan.kreuzBei(x, rathaus.bereich().vonZ() + 2),
                    "bei x " + x + " liegt die Kante");
            assertTrue(Bauplan.anDerLaube(rathaus, x, fuss + 2,
                            rathaus.bereich().vonZ() + 2),
                    "bei x " + x + " steht die Laubenwand");
            // UEBER DIE GANZE HOEHE UND TIEFE, nicht nur an einer Stelle: Eine Wand mit
            // einem Loch ist keine Wand.
            for (int y = fuss + 1; y <= fuss + Bauplan.LAUBE_HOEHE; y++) {
                for (int z = rathaus.bereich().vonZ() + 1;
                        z <= rathaus.bereich().bisZ() - 1; z++) {
                    assertEquals(Bauplan.Stueck.GEFACH,
                            Bauplan.stueckBei(rathaus, x, y, z),
                            "die Laubenwand bei " + x + "/" + y + "/" + z);
                }
            }
        }
        // UND DIE AUSSENWAND BEHAELT DAS WORT: Wo sie liegt, gilt sie - mit ihren
        // Fenstern. anDerLaube sagt nur "diese Spalte und diese Hoehe"; dass die
        // Aussenwand vorgeht, entscheidet stueckBei, und zwar daran, dass die
        // Laubenwand erst im Innenraum gefragt wird.
        Bauplan.Stueck amRand = Bauplan.stueckBei(rathaus, Stadtplan.RADIAL_HALB + 1,
                fuss + 2, rathaus.bereich().vonZ());
        assertNotEquals(Bauplan.Stueck.LUFT, amRand,
                "an der Aussenwand steht die Aussenwand: " + amRand);

        // UEBER DER LAUBE LIEGT EINE DECKE - sonst waere der Bau ein Tor und kein Haus.
        // Gesucht und nicht gerechnet: Direkt ueber der Oeffnung ist noch Innenraum, und
        // der ist hohl; die erste Geschossdecke liegt dort, wo auf der Fassade das
        // liegende Band sitzt. Genau das hat diese Probe mich gelehrt - der Block
        // unmittelbar ueber der Laube ist zu Recht Luft.
        // KEIN ZAHLENWERT ALS "NICHT GEFUNDEN": Altfurt liegt bei y -61, und die Decke
        // liegt bei -54. Ein Waechter auf > 0 verwirft jeden echten Treffer - genau das
        // ist am 24.11.2026 schon einmal passiert, in einem Weltmesslauf, und der
        // meldete dann null geprueefte Haeuser.
        boolean deckeGefunden = false;
        int deckeUeberDerLaube = 0;
        for (int y = fuss + Bauplan.LAUBE_HOEHE + 1; y <= Bauplan.dachansatz(rathaus);
                y++) {
            if (Bauplan.stueckBei(rathaus, 0, y, rathaus.bereich().vonZ() + 2)
                    == Bauplan.Stueck.DECKE) {
                deckeUeberDerLaube = y;
                deckeGefunden = true;
                break;
            }
        }
        assertTrue(deckeGefunden,
                "ueber der Laube liegt eine Geschossdecke - der Bau ist nicht offen");
        assertTrue(deckeUeberDerLaube <= fuss + Bauplan.LAUBE_HOEHE
                        + Bauplan.GESCHOSSHOEHE,
                "und zwar innerhalb eines Geschosses darueber: " + deckeUeberDerLaube);

        // EIN BAU, DER DEN ARM NUR HALB BERUEHRT, bekommt KEINE Laube - ein Durchgang
        // wuerde ihn abschneiden. Das Zunfthaus lag so, und darum ist es gewandert.
        Platzhalterbau halb = new Platzhalterbau("halbdrin",
                new Quader(-51, -61, -7, -41, -48, 3), Richtung.OST,
                Baustil.FACHWERK, Dachart.ZIEGEL);
        assertFalse(Bauplan.ueberspanntKreuzarm(halb),
                "es reicht nicht ueber den ganzen Arm");
        assertFalse(Bauplan.inDerLaube(halb, -46, -60, 0), "also keine Laube");

        // UND EIN BAU GANZ AUSSERHALB auch nicht.
        Platzhalterbau weitweg = new Platzhalterbau("weitweg",
                new Quader(60, -61, 60, 68, -48, 70), Richtung.NORD,
                Baustil.SCHIEFER, Dachart.ZIEGEL);
        assertFalse(Bauplan.ueberspanntKreuzarm(weitweg), "weit weg vom Arm");

        // OHNE BAU EINE KLAGE.
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.ueberspanntKreuzarm(null));
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.inDerLaube(null, 0, 0, 0));
    }

}
