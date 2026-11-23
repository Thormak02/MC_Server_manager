package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
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

    @Test
    @DisplayName("Giebelstufen und Dachansatz folgen aus der FRONT")
    void giebelUndDachansatz() {
        Platzhalterbau bau = haus();

        assertEquals(7, bau.front(), "sieben breit zur Gasse");
        assertEquals(11, bau.tiefe(), "elf tief");
        assertEquals(14, bau.hoehe());
        // (7 - 1) / 2 = 3, und der First sitzt genau auf der obersten Ebene.
        assertEquals(3, Bauplan.giebelstufen(bau));
        assertEquals(-51, Bauplan.dachansatz(bau), "-48 minus drei Stufen");

        // DIE PROBE AUF DIE REGEL, nicht auf die Zahl: Ein breiteres Haus hat mehr
        // Stufen. Ohne das waere die Rechnung auch mit einer festen 3 gruen.
        Platzhalterbau rathaus = new Platzhalterbau("bundesratshaus",
                new Quader(64, -61, 26, 80, -46, 38), Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.KUPFER);
        assertEquals(17, rathaus.front(), "traufstaendig und breit");
        assertEquals(8, Bauplan.giebelstufen(rathaus), "(17 - 1) / 2");
        assertEquals(-54, Bauplan.dachansatz(rathaus));
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
        // Der Sockel ist der Ring darueber: 11*7 minus die 9*5 im Inneren.
        assertEquals(11 * 7 - 9 * 5, gezaehlt.get(Bauplan.Stueck.SOCKEL));
        // Die Tuer ist zwei hoch, die Laterne zweimal einer.
        assertEquals(2, gezaehlt.get(Bauplan.Stueck.TUER));
        assertEquals(2, gezaehlt.get(Bauplan.Stueck.LATERNE));
        // Oben EIN Firstblock je Ebene - und nur die oberste Ebene ist First.
        assertEquals(11, gezaehlt.get(Bauplan.Stueck.FIRST),
                "der First laeuft ueber die ganze Tiefe");
        // Es gibt Fachwerk, Gefach, Fenster, Giebel und Dach - keines davon leer.
        for (Bauplan.Stueck stueck : new Bauplan.Stueck[]{Bauplan.Stueck.BALKEN,
                Bauplan.Stueck.GEFACH, Bauplan.Stueck.FENSTER,
                Bauplan.Stueck.GIEBELSTUFE, Bauplan.Stueck.DACH, Bauplan.Stueck.LUFT}) {
            assertTrue(gezaehlt.getOrDefault(stueck, 0) > 0,
                    stueck + " kommt gar nicht vor: " + gezaehlt);
        }
    }

    @Test
    @DisplayName("Der Treppengiebel steht an der FRONT, das Dach an den Laengsseiten")
    void treppengiebelStehtVorn() {
        // DAS IST DIE GANZE AUSSAGE VON "GIEBELSTAENDIG". Stuende der Giebel an der
        // langen Seite, waere es ein traufstaendiges Haus - und die Silhouette der Gasse
        // waere eine andere.
        Platzhalterbau bau = haus();

        // Erste Stufe: Einzug 1, also quer 4..8 statt 3..9.
        assertEquals(Bauplan.Stueck.GIEBELSTUFE, Bauplan.stueckBei(bau, 58, -50, 4),
                "die Front traegt die Stufe");
        assertEquals(Bauplan.Stueck.GIEBELSTUFE, Bauplan.stueckBei(bau, 48, -50, 8),
                "die Rueckwand auch - ein Haus hat zwei Giebel");
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(bau, 58, -50, 3),
                "was eingezogen ist, ist Luft");
        assertEquals(Bauplan.Stueck.DACH, Bauplan.stueckBei(bau, 53, -50, 4),
                "dazwischen liegt die Dachflaeche");
        assertEquals(Bauplan.Stueck.DACH, Bauplan.stueckBei(bau, 53, -50, 8));
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(bau, 53, -50, 6),
                "und darunter der Dachboden");

        // Zweite Stufe: Einzug 2, quer 5..7.
        assertEquals(Bauplan.Stueck.GIEBELSTUFE, Bauplan.stueckBei(bau, 58, -49, 5));
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(bau, 58, -49, 4));
        assertEquals(Bauplan.Stueck.DACH, Bauplan.stueckBei(bau, 53, -49, 5));

        // Oben: EIN Block, und der ist First - ueber die ganze Tiefe.
        assertEquals(Bauplan.Stueck.FIRST, Bauplan.stueckBei(bau, 58, -48, 6));
        assertEquals(Bauplan.Stueck.FIRST, Bauplan.stueckBei(bau, 53, -48, 6));
        assertEquals(Bauplan.Stueck.FIRST, Bauplan.stueckBei(bau, 48, -48, 6));
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(bau, 53, -48, 5),
                "neben dem First ist nichts mehr");
    }

    @Test
    @DisplayName("Die Tuer sitzt mittig in der Front, ueber dem Sockel, zwei hoch")
    void dieTuer() {
        Platzhalterbau bau = haus();

        assertEquals(-59, Bauplan.tuerUntersteEbene(bau), "gleich ueber dem Sockel");
        assertEquals(-58, Bauplan.tuerObersteEbene(bau));
        assertEquals(2, Bauplan.tuerHoehe(bau));
        // OST: die Front ist x = 58, die Mitte quer ist z = 6.
        assertEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(bau, 58, -59, 6));
        assertEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(bau, 58, -58, 6));
        assertEquals(Bauplan.Stueck.SOCKEL, Bauplan.stueckBei(bau, 58, -60, 6),
                "darunter der Sockel");
        assertNotEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(bau, 58, -57, 6),
                "darueber nicht mehr");
        assertNotEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(bau, 48, -59, 6),
                "und nicht in der Rueckwand");

        Bauplan.Stelle tuer = Bauplan.tuer(bau);
        assertEquals(new Bauplan.Stelle(58, -59, 6, Richtung.OST), tuer,
                "sie zeigt nach aussen");

        // DIE VIER SEITEN SIND VERSCHIEDEN - eine Richtung ohne Wirkung waere eine
        // Einstellung ohne Wirkung.
        Map<Richtung, Bauplan.Stelle> stellen = new EnumMap<>(Richtung.class);
        for (Richtung richtung : Richtung.values()) {
            stellen.put(richtung, Bauplan.tuer(haus(richtung)));
        }
        assertEquals(4, stellen.values().stream().distinct().count(),
                "vier Seiten, vier Stellen: " + stellen);
        assertEquals(new Bauplan.Stelle(53, -59, 3, Richtung.NORD),
                stellen.get(Richtung.NORD), "Norden ist negatives Z");
    }

    @Test
    @DisplayName("Die Laterne haengt zwei Bloecke neben der Tuer")
    void dieLaterne() {
        Platzhalterbau bau = haus();

        assertEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 58, -58, 4));
        assertEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 58, -58, 8));
        assertNotEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 58, -58, 5),
                "nicht direkt daneben");
        assertNotEquals(Bauplan.Stueck.LATERNE, Bauplan.stueckBei(bau, 53, -58, 3),
                "und nicht an der Laengsseite");
    }

    @Test
    @DisplayName("Das Fachwerk hat seinen Takt: Ecken, jede dritte Spalte, ein Band je Geschoss")
    void dasFachwerk() {
        Platzhalterbau bau = haus();

        // Die vier Ecksaeulen - auf jeder Wandebene.
        for (int y = -59; y <= -51; y++) {
            assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 48, y, 3),
                    "Ecke auf y " + y);
            assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 58, y, 9),
                    "Ecke auf y " + y);
        }
        // Das liegende Band schliesst das Geschoss ab: Sockel ist -60, vier hoeher ist
        // -56, und der Dachansatz -51 traegt das oberste Band.
        for (int x = 49; x <= 57; x++) {
            assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, x, -56, 3),
                    "Band auf x " + x);
            assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, x, -51, 3),
                    "oberstes Band auf x " + x);
        }
        // Stehende Balken im Takt drei, von der Ecke gezaehlt: an der Laengsseite also
        // x 51 und x 55 (Abstand 3 von beiden Ecken).
        assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 51, -57, 3));
        assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 55, -57, 3));
        assertNotEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 52, -57, 3),
                "dazwischen nicht");

        // VON DER ECKE GEZAEHLT und nicht vom Weltnullpunkt: Zwei gleich gebaute Haeuser
        // muessen gleich aussehen, auch wenn ihr x um eins verschoben ist.
        Platzhalterbau verschoben = new Platzhalterbau("zwilling",
                new Quader(49, -61, 3, 59, -48, 9), Richtung.OST,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);
        assertEquals(Bauplan.stueckBei(bau, 51, -57, 3),
                Bauplan.stueckBei(verschoben, 52, -57, 3),
                "derselbe Takt, nur um eins verschoben");
    }

    @Test
    @DisplayName("Die Fenster liegen zwischen den Baendern - zwei Ebenen je Geschoss")
    void dieFenster() {
        Platzhalterbau bau = haus();

        // Sockel -60, Band -56: dazwischen sind -59 und -58 die Fensterebenen, -57 ist
        // die Bruestung darueber... gerechnet: ueberSockel 2 und 3.
        assertEquals(Bauplan.Stueck.FENSTER, Bauplan.stueckBei(bau, 52, -58, 3));
        assertEquals(Bauplan.Stueck.FENSTER, Bauplan.stueckBei(bau, 52, -57, 3));
        assertEquals(Bauplan.Stueck.GEFACH, Bauplan.stueckBei(bau, 52, -59, 3),
                "unter dem Fenster das Gefach");
        assertEquals(Bauplan.Stueck.BALKEN, Bauplan.stueckBei(bau, 52, -56, 3),
                "darueber das Band");
        // Im naechsten Geschoss wieder, vier Ebenen hoeher.
        assertEquals(Bauplan.Stueck.FENSTER, Bauplan.stueckBei(bau, 52, -54, 3));
        assertEquals(Bauplan.Stueck.FENSTER, Bauplan.stueckBei(bau, 52, -53, 3));
        // Kein Fenster im Sockel - ein Fenster auf Bodenhoehe waere ein Loch.
        assertEquals(Bauplan.Stueck.SOCKEL, Bauplan.stueckBei(bau, 52, -60, 3));
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

        assertEquals(new Bauplan.Stelle(57, -57, 6, Richtung.WEST), Bauplan.schild(bau),
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
    @DisplayName("Ein Turm von drei Bloecken Front hat EINE Giebelstufe")
    void schlankerTurm() {
        // Die Tortuerme der ausgelieferten Datei: drei breit, sieben tief, sechzehn hoch.
        Platzhalterbau turm = new Platzhalterbau("forsttor",
                new Quader(70, -61, -7, 72, -46, -1), Richtung.SUED,
                Baustil.FORST, Dachart.KUPFER);

        assertEquals(3, turm.front());
        assertEquals(1, Bauplan.giebelstufen(turm), "(3 - 1) / 2");
        assertEquals(-47, Bauplan.dachansatz(turm));
        // Oben bleibt genau ein Block uebrig - der First.
        assertEquals(Bauplan.Stueck.FIRST, Bauplan.stueckBei(turm, 71, -46, -4));
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(turm, 70, -46, -4),
                "daneben nichts mehr");
        // Und die Tuer passt noch hinein.
        assertEquals(2, Bauplan.tuerHoehe(turm));
        assertEquals(new Bauplan.Stelle(71, -59, -1, Richtung.SUED), Bauplan.tuer(turm));
    }
}
