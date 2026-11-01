package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Welche Bloecke eine Gruppe fuellt, welche sie zumauert - und welche nicht.
 *
 * <p>Die Zahlen hier sind vorgerechnet und nicht abgelesen. Bei Raster 64 + 7 ist das Band
 * zwischen zwei Zellen 7 Bloecke breit und 64 lang, also 448 Bloecke; davon sind 2 x 64
 * Mauer und 5 x 64 Weg. Eine Kreuzung ist 7 x 7 = 49. Je Bandende kommen 5 Mauerstuecke
 * dazu - so breit ist die Querstrasse, die dort gegen das neue Bauland laeuft.
 */
class GruppenflaecheTest {

    private static final String WELT = "freimarken:marktstadt";

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final UUID anna = UUID.randomUUID();

    private Parzelle feld(int x, int z) {
        return new Parzelle(1L, WELT, x, z, anna, Set.of(), 0L, Instant.EPOCH,
                Art.PARZELLE, 7L, Zustand.AKTIV);
    }

    private Gruppenflaeche flaeche(int... xz) {
        List<Parzelle> felder = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            felder.add(feld(xz[i], xz[i + 1]));
        }
        return Gruppenflaeche.aus(felder);
    }

    /** Die ganze Naht, als Liste von "x/z/ohneGruppe/mitGruppe". */
    private List<String> naht(Gruppenflaeche flaeche) {
        List<String> gefunden = new ArrayList<>();
        flaeche.ueberDieNaht(plan, (x, z, ohneGruppe, mitGruppe) ->
                gefunden.add(x + "/" + z + "/" + ohneGruppe + "/" + mitGruppe));
        return gefunden;
    }

    /** Die Naht ohne die Stellen, die eine fruehere Gruppe schon genauso belegt. */
    private List<String> naht(Gruppenflaeche flaeche, List<Gruppenflaeche> schonGelegt) {
        List<String> gefunden = new ArrayList<>();
        flaeche.ueberDieNaht(plan, schonGelegt, (x, z, ohneGruppe, mitGruppe) ->
                gefunden.add(x + "/" + z + "/" + ohneGruppe + "/" + mitGruppe));
        return gefunden;
    }

    /** Die Stellen, aus denen Bauland wird. */
    private List<String> fugen(Gruppenflaeche flaeche) {
        return naht(flaeche).stream().filter(stelle -> stelle.endsWith("/PARZELLE")).toList();
    }

    /** Die Stellen, an denen die Mauer den Ring wieder schliesst. */
    private List<String> mauerstuecke(Gruppenflaeche flaeche) {
        return naht(flaeche).stream().filter(stelle -> stelle.endsWith("/MAUER")).toList();
    }

    /** Wie viele dieser Stellen ohne die Gruppe dieses Feld waeren. */
    private long davon(List<String> stellen, Rasterplan.Feld ohneGruppe) {
        return stellen.stream()
                .filter(stelle -> stelle.split("/")[2].equals(ohneGruppe.name()))
                .count();
    }

    @Test
    @DisplayName("Ein einzelnes Feld veraendert nichts")
    void einFeldVeraendertNichts() {
        // Nicht etwa den eigenen Rand: Jenseits des Bandes liegt eine Zelle, die nicht
        // dazugehoert. Ein Feld allein aendert in der Welt also gar nichts - und genau
        // deshalb ist es keine Gruppe.
        assertEquals(List.of(), naht(flaeche(0, 0)));
        assertEquals(1, flaeche(0, 0).felder());
    }

    @Test
    @DisplayName("Zwei Felder nebeneinander ergeben ein Band aus 448 Bloecken")
    void zweiFelderEinBand() {
        List<String> fugen = fugen(flaeche(0, 0, 1, 0));

        assertEquals(448, fugen.size(), "7 breit, 64 lang");
        assertEquals(128, davon(fugen, Rasterplan.Feld.MAUER), "zwei Mauerreihen");
        assertEquals(320, davon(fugen, Rasterplan.Feld.WEG), "fuenf Wegreihen");
        assertEquals(0, davon(fugen, Rasterplan.Feld.PARZELLE), "Bauland ist keine Fuge");
    }

    @Test
    @DisplayName("Das Band liegt genau zwischen den beiden Feldern")
    void bandLiegtDazwischen() {
        for (String fuge : fugen(flaeche(0, 0, 1, 0))) {
            int x = Integer.parseInt(fuge.split("/")[0]);
            int z = Integer.parseInt(fuge.split("/")[1]);
            assertTrue(x >= 64 && x <= 70, "x gehoert ins Band, nicht " + x);
            assertTrue(z >= 0 && z <= 63, "z bleibt auf Baulandhoehe, nicht " + z);
        }
    }

    @Test
    @DisplayName("An beiden Bandenden schliesst die Mauer den Ring")
    void bandendenWerdenZugemauert() {
        List<String> mauerstuecke = mauerstuecke(flaeche(0, 0, 1, 0));

        // Das Band endet bei z = -1 und z = 64 an der Querstrasse. Dort ist die Z-Achse
        // Mauer, die X-Achse aber Weg - und weil der Weg gewinnt, laege dort Pflaster.
        assertEquals(10, mauerstuecke.size(), "zwei Enden, je 5 Bloecke Strassenbreite");
        assertEquals(10, davon(mauerstuecke, Rasterplan.Feld.WEG),
                "zugemauert wird nur Weg - wo schon Mauer steht, ist nichts zu tun");
        Set<String> erwartet = new HashSet<>();
        for (int x = 65; x <= 69; x++) {
            erwartet.add(x + "/-1/WEG/MAUER");
            erwartet.add(x + "/64/WEG/MAUER");
        }
        assertEquals(erwartet, new HashSet<>(mauerstuecke));
    }

    @Test
    @DisplayName("Bei einer L-Form bleibt die Kreuzung aussen")
    void lFormLaesstDieKreuzungAus() {
        // 0/0, 1/0 und 1/1: An der Kreuzung bei 64..70 / 64..70 liegt auch die Zelle 0/1,
        // und die gehoert jemand anderem. Zwei Baender, keine Kreuzung.
        Gruppenflaeche lForm = flaeche(0, 0, 1, 0, 1, 1);
        List<String> fugen = fugen(lForm);

        assertEquals(2 * 448, fugen.size());
        assertFalse(fugen.contains("67/67/WEG/PARZELLE"), "die Kreuzung bleibt Weg");

        // Vier Bandenden - zwei davon liegen an der Kreuzung, die weiter oeffentlich ist.
        List<String> mauerstuecke = mauerstuecke(lForm);
        assertEquals(20, mauerstuecke.size());
        assertTrue(mauerstuecke.contains("67/64/WEG/MAUER"), "das Band endet an der Kreuzung");
        assertTrue(mauerstuecke.contains("70/67/WEG/MAUER"), "das quere Band ebenso");
        assertFalse(mauerstuecke.contains("67/67/WEG/MAUER"),
                "die Kreuzung selbst bleibt frei - sonst waere die Strasse dicht");
    }

    @Test
    @DisplayName("Vier Felder im Quadrat nehmen die Kreuzung mit: 1841 Bloecke")
    void quadratNimmtDieKreuzungMit() {
        Gruppenflaeche quadrat = flaeche(0, 0, 1, 0, 0, 1, 1, 1);
        List<String> fugen = fugen(quadrat);

        assertEquals(4 * 448 + 49, fugen.size(), "vier Baender und eine Kreuzung");
        assertTrue(fugen.contains("67/67/WEG/PARZELLE"));
        // In der Kreuzung ist nur Mauer, wo BEIDE Achsen Mauer sagen - sonst gewinnt der
        // Weg, damit sich Wege kreuzen koennen.
        assertEquals(4 * 128 + 4, davon(fugen, Rasterplan.Feld.MAUER));
        // Die Kreuzung in der Mitte liegt innen. Zu schliessen sind die vier Enden des
        // Kreuzes nach aussen, nicht acht einzelne Bandenden.
        assertEquals(20, mauerstuecke(quadrat).size());
    }

    @Test
    @DisplayName("Auf der negativen Seite stimmt die Flaeche auch")
    void negativeSeite() {
        // Zelle -21 beginnt bei -1491 und hat ihr Band bei -1427 bis -1421. Ohne die
        // Vorzeichenmaske im Zellschluessel waere -21/-21 dieselbe Zelle wie -20/-21,
        // und die Flaeche haette nur ein Feld.
        Gruppenflaeche zwei = flaeche(-21, -21, -20, -21);
        assertEquals(2, zwei.felder());

        List<String> fugen = fugen(zwei);
        assertEquals(448, fugen.size());
        for (String fuge : fugen) {
            int x = Integer.parseInt(fuge.split("/")[0]);
            assertTrue(x >= -1427 && x <= -1421, "x gehoert ins Band, nicht " + x);
        }
        assertEquals(10, mauerstuecke(zwei).size());
    }

    @Test
    @DisplayName("Keine Koordinate kommt zweimal vor")
    void jedeStelleNurEinmal() {
        // Jede Stelle wird genau einmal geschrieben. Zweimal waere nicht falsch, aber die
        // zurueckgemeldete Blockzahl stimmte dann nicht mehr.
        Set<String> orte = new HashSet<>();
        for (String stelle : naht(flaeche(0, 0, 1, 0, 0, 1, 1, 1))) {
            String[] teile = stelle.split("/");
            assertTrue(orte.add(teile[0] + "/" + teile[1]), "doppelt: " + stelle);
        }
        assertEquals(4 * 448 + 49 + 20, orte.size());
    }

    @Test
    @DisplayName("Nach dem Zusammenlegen ist die Flaeche rundum geschlossen")
    void rundumGeschlossen() {
        // Die Probe aufs Ganze, und die einzige, die das gemeldete Fehlerbild trifft:
        // Geprueft wird nicht der Rechenweg, sondern das Bild in der Welt. Neben Bauland
        // darf Bauland liegen oder Mauer - nie Weg. Ohne die Mauerstuecke an den
        // Bandenden lief die Querstrasse ohne Umrandung ins Grundstueck.
        istRundumGeschlossen(0, 0);
        istRundumGeschlossen(0, 0, 1, 0);
        istRundumGeschlossen(0, 0, 0, 1);
        istRundumGeschlossen(0, 0, 1, 0, 2, 0);
        istRundumGeschlossen(0, 0, 1, 0, 1, 1);
        istRundumGeschlossen(0, 0, 1, 0, 0, 1, 1, 1);
        istRundumGeschlossen(-21, -21, -20, -21);
        istRundumGeschlossen(-1, 0, 0, 0);
    }

    @Test
    @DisplayName("Zwei lose Felder lassen nichts aus")
    void loseFelderLassenNichtsAus() {
        // Der haeufige Fall: zwei einzelne Parzellen. Keine von beiden hat eine Naht, also
        // ist alles neu zu legen - sonst bliebe die erste Fuge der Stadt eine Strasse.
        assertEquals(naht(flaeche(0, 0, 1, 0)),
                naht(flaeche(0, 0, 1, 0), List.of(flaeche(0, 0), flaeche(1, 0))));
    }

    @Test
    @DisplayName("Ein angehaengtes Feld laesst die alte Fuge liegen")
    void angehaengtesFeldLaesstDieAlteFugeLiegen() {
        // Die alte Fuge ist seit dem ersten Zusammenlegen Bauland, und der Spieler hat
        // darauf gebaut. Sie noch einmal zu fuellen setzt sein Erdgeschoss auf Gras
        // zurueck und loescht die Schicht darueber - 448 Stellen, ohne Warnung.
        Gruppenflaeche vorher = flaeche(0, 0, 1, 0);
        List<String> neu = naht(flaeche(0, 0, 1, 0, 1, 1), List.of(vorher));

        assertEquals(448 + 10, neu.size(), "nur das neue Band und seine zwei Enden");
        for (String stelle : neu) {
            int x = Integer.parseInt(stelle.split("/")[0]);
            int z = Integer.parseInt(stelle.split("/")[1]);
            assertFalse(x >= 64 && x <= 70 && z >= 0 && z <= 63,
                    "das liegt in der alten Fuge: " + stelle);
        }
        assertTrue(neu.contains("100/67/WEG/PARZELLE"), "das neue Band kommt");
        assertTrue(neu.contains("70/67/WEG/MAUER"), "sein Ende wird zugemauert");
        assertFalse(neu.contains("67/64/WEG/MAUER"), "die alte Mauer steht schon");
    }

    @Test
    @DisplayName("Beim vierten Feld wird aus dem Bandende die Kreuzung")
    void bandendeWirdZurKreuzung() {
        // Die Gegenprobe zum Auslassen: Verglichen wird das Ergebnis, nicht die Teilnahme.
        // Wo vorhin ein Bandende zugemauert wurde, liegt jetzt die Kreuzung mitten im
        // Grundstueck - die Mauer muss weg, obwohl die alte Gruppe die Stelle auch hatte.
        Gruppenflaeche lForm = flaeche(0, 0, 1, 0, 1, 1);
        List<String> neu = naht(flaeche(0, 0, 1, 0, 0, 1, 1, 1), List.of(lForm));

        assertEquals(2 * 448 + 49 + 10, neu.size(), "zwei Baender, die Kreuzung, zwei Enden");
        assertTrue(neu.contains("67/64/WEG/PARZELLE"), "aus der alten Mauer wird Bauland");
        assertTrue(neu.contains("70/67/WEG/PARZELLE"));
        assertTrue(neu.contains("67/67/WEG/PARZELLE"), "die Kreuzung selbst");
        assertFalse(neu.contains("67/30/WEG/PARZELLE"), "die alte Fuge bleibt liegen");
        assertEquals(10, neu.stream().filter(stelle -> stelle.endsWith("/MAUER")).count(),
                "zuzumauern sind nur die Enden der neuen Baender");
    }

    @Test
    @DisplayName("Was ausgelassen wird, liegt schon richtig")
    void ausgelassenesLiegtSchonRichtig() {
        // Die Probe aufs Auslassen, und die einzige, die zaehlt: Jede Stelle der vollen
        // Naht ist entweder neu zu legen, oder die fruehere Gruppe hat dort schon dasselbe
        // Ergebnis. Faellt eine Stelle durch beide Raster, bleibt in der Welt ein Streifen
        // Pflaster mitten im Grundstueck - oder ein Loch in der Mauer.
        // Die erwartete Zahl gehoert dazu: Ohne sie laeuft dieser Test mit null
        // Assertions gruen durch, sobald die Auslassung ganz entfaellt - dann ist jede
        // Stelle neu, und die Schleife springt ueber alle hinweg.
        pruefeAuslassung(flaeche(0, 0), flaeche(0, 0, 1, 0), 0);
        pruefeAuslassung(flaeche(0, 0, 1, 0), flaeche(0, 0, 1, 0, 1, 1), 448 + 10);
        pruefeAuslassung(flaeche(0, 0, 1, 0), flaeche(0, 0, 1, 0, 2, 0), 448 + 10);
        // Beim Quadrat bleiben 10 der 20 Mauerstuecke nicht liegen: Sie werden zur
        // Kreuzung. Ausgelassen sind also 896 Fugen und nur 10 Mauerstuecke.
        pruefeAuslassung(flaeche(0, 0, 1, 0, 1, 1), flaeche(0, 0, 1, 0, 0, 1, 1, 1),
                2 * 448 + 10);
        pruefeAuslassung(flaeche(-21, -21, -20, -21),
                flaeche(-21, -21, -20, -21, -20, -20), 448 + 10);
    }

    @Test
    @DisplayName("Ohne ein Randfeld bleibt die Gruppe ganz")
    void ohneRandfeldBleibtGanz() {
        // Was herausgeloest werden darf: ein Feld, an dem die Gruppe endet.
        assertTrue(flaeche(0, 0, 1, 0).bleibtGanzOhne(1, 0));
        assertTrue(flaeche(0, 0, 1, 0, 2, 0).bleibtGanzOhne(2, 0), "das Ende einer Reihe");
        assertTrue(flaeche(0, 0, 1, 0, 2, 0).bleibtGanzOhne(0, 0), "das andere Ende");
        assertTrue(flaeche(0, 0, 1, 0, 0, 1, 1, 1).bleibtGanzOhne(1, 1), "eine Ecke");
        assertTrue(flaeche(-21, -21, -20, -21).bleibtGanzOhne(-20, -21), "negative Seite");
    }

    @Test
    @DisplayName("Ohne das tragende Feld laege die Gruppe in zwei Teilen")
    void ohneDasTragendeFeldZweiTeile() {
        // Die Reihe 0/0 - 1/0 - 2/0: Nimmt man die Mitte heraus, beruehren sich die Reste
        // nicht mehr. Die Fuge zwischen ihnen waere Bauland ohne Verbindung zum eigenen
        // Grundstueck, mitten auf einer Strasse, die wieder oeffentlich sein muesste.
        assertFalse(flaeche(0, 0, 1, 0, 2, 0).bleibtGanzOhne(1, 0));
        // Dieselbe Lage um die Ecke: 0/0 - 1/0 - 1/1, die Mitte ist 1/0.
        assertFalse(flaeche(0, 0, 1, 0, 1, 1).bleibtGanzOhne(1, 0));
    }

    @Test
    @DisplayName("Ueber die Ecke haelt nichts zusammen")
    void ueberDieEckeHaeltNichts() {
        // Zwei Felder, die sich nur an einer Ecke beruehren, haben keine Fuge zwischen
        // sich - dort liegt die Kreuzung, und die gehoert allen. Im Quadrat ist deshalb
        // jedes Feld entbehrlich, aber in der L-Form nicht das mittlere.
        assertTrue(flaeche(0, 0, 1, 0, 0, 1, 1, 1).bleibtGanzOhne(0, 0),
                "im Quadrat ist jedes Feld entbehrlich - die anderen drei haben Kanten");
        // Drei Felder auf der Diagonale beruehren sich nur an Ecken. Eine solche Gruppe
        // kann durch Zusammenlegen nie entstehen; wenn sie doch vorliegt, sagt die
        // Pruefung es auch - der Rest haengt nicht zusammen.
        assertFalse(flaeche(0, 0, 1, 1, 2, 2).bleibtGanzOhne(1, 1));
    }

    @Test
    @DisplayName("Ein einzelnes Feld und ein fremdes Feld aendern nichts")
    void einzelnesUndFremdesFeld() {
        // Ein Feld allein bleibt ganz, wenn es selbst herausgeht - was bleibt, ist nichts,
        // und nichts ist zusammenhaengend. Ein Feld, das nicht dazugehoert, aendert nichts.
        assertTrue(flaeche(0, 0).bleibtGanzOhne(0, 0));
        assertTrue(flaeche(0, 0, 1, 0, 2, 0).bleibtGanzOhne(9, 9), "gehoert gar nicht dazu");
        assertFalse(flaeche(0, 0, 1, 0, 3, 0).bleibtGanzOhne(9, 9),
                "diese Flaeche war schon vorher in zwei Teilen");
    }

    @Test
    @DisplayName("Eine Flaeche ohne Felder gibt es nicht")
    void ohneFelderLaut() {
        // Lieber ein Abbruch als eine leere Flaeche: Beim Zurueckbau heisst "keine Fuge"
        // sonst "nichts zu tun", und der Weg bliebe fuer immer Bauland.
        assertThrows(IllegalArgumentException.class, () -> Gruppenflaeche.aus(List.of()));
    }

    private void pruefeAuslassung(Gruppenflaeche vorher, Gruppenflaeche nachher,
                                  int erwarteteAuslassungen) {
        Map<String, Rasterplan.Feld> alt = new HashMap<>();
        vorher.ueberDieNaht(plan, (x, z, ohneGruppe, mitGruppe) ->
                alt.put(x + "/" + z, mitGruppe));
        List<String> volle = naht(nachher);
        Set<String> neu = new HashSet<>(naht(nachher, List.of(vorher)));
        int ausgelassen = 0;
        for (String stelle : volle) {
            if (neu.contains(stelle)) {
                continue;
            }
            ausgelassen++;
            String[] teile = stelle.split("/");
            assertEquals(Rasterplan.Feld.valueOf(teile[3]), alt.get(teile[0] + "/" + teile[1]),
                    "ausgelassen, aber die alte Gruppe legt dort etwas anderes: " + stelle);
        }
        assertEquals(erwarteteAuslassungen, ausgelassen, "Zahl der ausgelassenen Stellen");
        assertEquals(volle.size(), neu.size() + ausgelassen,
                "jede Stelle der vollen Naht ist entweder neu oder ausgelassen");
    }

    private void istRundumGeschlossen(int... xz) {
        Gruppenflaeche flaeche = flaeche(xz);
        Map<String, Rasterplan.Feld> naht = new HashMap<>();
        flaeche.ueberDieNaht(plan, (x, z, ohneGruppe, mitGruppe) ->
                naht.put(x + "/" + z, mitGruppe));

        int vonZelleX = Integer.MAX_VALUE;
        int vonZelleZ = Integer.MAX_VALUE;
        int bisZelleX = Integer.MIN_VALUE;
        int bisZelleZ = Integer.MIN_VALUE;
        for (int i = 0; i < xz.length; i += 2) {
            vonZelleX = Math.min(vonZelleX, xz[i]);
            bisZelleX = Math.max(bisZelleX, xz[i]);
            vonZelleZ = Math.min(vonZelleZ, xz[i + 1]);
            bisZelleZ = Math.max(bisZelleZ, xz[i + 1]);
        }
        int bisWeltX = plan.ursprungX(bisZelleX) + plan.parzellenKante() - 1;
        int bisWeltZ = plan.ursprungZ(bisZelleZ) + plan.parzellenKante() - 1;
        for (int x = plan.ursprungX(vonZelleX); x <= bisWeltX; x++) {
            for (int z = plan.ursprungZ(vonZelleZ); z <= bisWeltZ; z++) {
                if (!istGruppenland(flaeche, naht, x, z)) {
                    continue;
                }
                pruefeNachbarn(naht, x, z);
            }
        }
    }

    private void pruefeNachbarn(Map<String, Rasterplan.Feld> naht, int x, int z) {
        pruefeNachbar(naht, x, z, x - 1, z);
        pruefeNachbar(naht, x, z, x + 1, z);
        pruefeNachbar(naht, x, z, x, z - 1);
        pruefeNachbar(naht, x, z, x, z + 1);
    }

    private void pruefeNachbar(Map<String, Rasterplan.Feld> naht, int x, int z,
            int nachbarX, int nachbarZ) {
        assertNotEquals(Rasterplan.Feld.WEG, bodenBei(naht, nachbarX, nachbarZ),
                "offene Flanke: neben dem Bauland " + x + "/" + z + " liegt bei "
                        + nachbarX + "/" + nachbarZ + " ein Weg ohne Mauer davor");
    }

    /** Was nach dem Zusammenlegen an dieser Stelle liegt. */
    private Rasterplan.Feld bodenBei(Map<String, Rasterplan.Feld> naht, int x, int z) {
        return naht.getOrDefault(x + "/" + z, plan.feldBei(x, z));
    }

    /** Bauland der Gruppe - nicht das des Nachbarn, das im selben Kasten liegt. */
    private boolean istGruppenland(Gruppenflaeche flaeche, Map<String, Rasterplan.Feld> naht,
            int x, int z) {
        if (bodenBei(naht, x, z) != Rasterplan.Feld.PARZELLE) {
            return false;
        }
        return naht.containsKey(x + "/" + z)
                || flaeche.gehoert(plan.parzelleX(x), plan.parzelleZ(z));
    }
}
