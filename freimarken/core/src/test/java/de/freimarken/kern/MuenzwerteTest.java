package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Der Katalog des Muenzhauses - seine Schranken und die Reihenfolge der Datei. */
@DisplayName("Die Muenzwerte")
class MuenzwerteTest {

    private static Muenzwerte.Posten hut(long preis) {
        return new Muenzwerte.Posten("kuerbishut", Map.of("carved_pumpkin", 1), preis,
                Map.of());
    }

    @Test
    @DisplayName("Die Vorgabe ist genau das, was Plan 8.1 fuer Kosmetik nennt")
    void vorgabeIstDerPlan() {
        // Zeichengenau gegen den PLAN und nicht gegen die eigenen Konstanten: Plan 8.1
        // nennt "Partikel, Hut, Rangfarbe 2-5 Goldmark". Wer die Preise hochdreht, soll
        // hier aufschlagen - 50 Goldmark sind zum Kurs des Plans 50.000 Mark, also
        // zwanzig Spielstunden fuer einen Hut.
        assertEquals(4, Muenzwerte.VORGABE_KATALOG.size());
        for (Muenzwerte.Posten posten : Muenzwerte.VORGABE_KATALOG) {
            assertTrue(posten.preis() >= 2L && posten.preis() <= 5L,
                    posten.schluessel() + " kostet " + posten.preis()
                            + " Goldmark - Plan 8.1 nennt 2 bis 5");
        }
        assertTrue(Muenzwerte.VORGABE.greift());
    }

    @Test
    @DisplayName("Ein Posten OHNE Gegenstaende kommt nicht zustande")
    void postenOhneGegenstand() {
        // DER RIEGEL DIESES BAUSCHRITTES, und er ist schaerfer als der der
        // Herausforderungen: Dort waere eine Aufgabe ohne Verlangtes "Geld fuer das
        // Tippen eines Befehls". Hier wuerde der Preis ABGEBUCHT, geliefert kaeme
        // nichts, und der Bescheid lautete GEKAUFT.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Muenzwerte.Posten("leer", Map.of(), 2L, Map.of()));

        assertTrue(fehler.getMessage().contains("liefert nichts"), fehler.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new Muenzwerte.Posten("leer", null, 2L, Map.of()));
    }

    @Test
    @DisplayName("Die Reihenfolge der Gegenstaende bleibt die der Datei")
    void reihenfolgeBleibt() {
        // Gemessen mit DREI Zeilen, und das ist der Punkt: Mit zwei kann eine unsortierte
        // Karte zufaellig richtig liegen. Die Reihenfolge steht in EINER Zeile der Tafel,
        // und Map.copyOf sagt keine zu - die Zeile stuende dann je Lauf der Maschine
        // anders da, und eine Bildprobe darauf waere nicht zu halten.
        Map<String, Integer> gegeben = new LinkedHashMap<>();
        gegeben.put("rose_bush", 16);
        gegeben.put("peony", 8);
        gegeben.put("lilac", 4);

        var posten = new Muenzwerte.Posten("blumen", gegeben, 2L, Map.of());

        assertEquals(List.of("rose_bush", "peony", "lilac"),
                new ArrayList<>(posten.gegenstaende().keySet()));
    }

    @Test
    @DisplayName("Die Karte des Aufrufers aendert den Posten nicht mehr")
    void postenIstUnveraenderlich() {
        Map<String, Integer> gegeben = new LinkedHashMap<>();
        gegeben.put("carved_pumpkin", 1);
        var posten = new Muenzwerte.Posten("hut", gegeben, 2L, Map.of());

        gegeben.put("bell", 1);

        assertEquals(1, posten.gegenstaende().size(),
                "der Posten haelt eine KOPIE, nicht die Karte des Aufrufers");
        assertThrows(UnsupportedOperationException.class,
                () -> posten.gegenstaende().put("bell", 1));
    }

    @Test
    @DisplayName("Ein Preis von 0 ist erlaubt und heisst gratis")
    void gratisErlaubt() {
        // Die Begruendung steht in Kasse.java: "Ein Betrag von 0 laesst sich immer
        // abbuchen. Sonst muesste jede Regel, die etwas gratis vergibt, den Sonderfall
        // selbst kennen." Ein Begruessungsgeschenk im Katalog ist eine sinnvolle
        // Einstellung.
        assertEquals(0L, hut(0L).preis());
        assertTrue(Muenzwerte.brauchbarerPreis(0L));
        assertFalse(Muenzwerte.brauchbarerPreis(-1L));
        assertFalse(Muenzwerte.brauchbarerPreis(Muenzwerte.HOECHSTER_PREIS + 1L));
    }

    @Test
    @DisplayName("Der Hoechstpreis selbst ist gueltig - die Grenze wird GENAU getroffen")
    void hoechstpreisIstGueltig() {
        // BEFUND DER PRUEFRUNDE (20.11.2026): Geprueft war nur Hoechstpreis+1, also waren
        // "<" und "<=" dasselbe. Mit "<" faellt ein Posten auf genau 1000000 mit einer
        // Klage heraus, die sich selbst widerspricht ("liegt nicht zwischen 0 und
        // 1000000"), und der Katalog ist stillschweigend kuerzer. Beim Kistenshop haelt
        // KistenshopTest den Hoechstpreis seit Monaten ausdruecklich als gueltig.
        assertTrue(Muenzwerte.brauchbarerPreis(Muenzwerte.HOECHSTER_PREIS));
        assertEquals(Muenzwerte.HOECHSTER_PREIS,
                hut(Muenzwerte.HOECHSTER_PREIS).preis());
    }

    @Test
    @DisplayName("Ein Preis ueber der Schranke kommt nicht zustande")
    void preisUeberDerSchranke() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> hut(Muenzwerte.HOECHSTER_PREIS + 1L));

        assertTrue(fehler.getMessage().contains("Goldmark"),
                "die Meldung muss die EINHEIT nennen: " + fehler.getMessage());
    }

    @Test
    @DisplayName("Die Stueckzahl haengt an den Lagerplaetzen eines Spielers")
    void stueckzahlAnDenLagerplaetzen() {
        // 36 Lagerplaetze mal 64 Stueck. Eine eigene Zahl und nicht die 3.456 der
        // Herausforderungen: Jene ist eine Doppelkiste, aus der HERAUSgenommen wird.
        assertEquals(36 * 64, Muenzwerte.HOECHSTE_STUECKZAHL);
        assertTrue(Muenzwerte.brauchbareStueckzahl(Muenzwerte.HOECHSTE_STUECKZAHL));
        assertFalse(Muenzwerte.brauchbareStueckzahl(Muenzwerte.HOECHSTE_STUECKZAHL + 1));
        assertFalse(Muenzwerte.brauchbareStueckzahl(0));
        assertFalse(Muenzwerte.brauchbareStueckzahl(null));
        assertThrows(IllegalArgumentException.class, () -> new Muenzwerte.Posten("zu-viel",
                Map.of("stone", Muenzwerte.HOECHSTE_STUECKZAHL + 1), 1L, Map.of()));
    }

    @Test
    @DisplayName("Eine Bedingung von 0 kommt nicht zustande - sie wirkte nie")
    void nullIstKeineBedingung() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Muenzwerte.Posten("hut", Map.of("carved_pumpkin", 1), 2L,
                        Map.of(Bedingungsart.PARZELLEN, 0L)));

        assertTrue(fehler.getMessage().contains("parzellen"), fehler.getMessage());
    }

    @Test
    @DisplayName("Eine Bedingung ueber dem Hoechstwert ihrer ART kommt nicht zustande")
    void bedingungUeberDerArt() {
        // Je Art ihre eigene Schranke - beim Beruf ist sie 1, weil es ein Ja/Nein ist.
        assertThrows(IllegalArgumentException.class,
                () -> new Muenzwerte.Posten("hut", Map.of("carved_pumpkin", 1), 2L,
                        Map.of(Bedingungsart.BERUF, 2L)));
        assertEquals(1L, new Muenzwerte.Posten("hut", Map.of("carved_pumpkin", 1), 2L,
                Map.of(Bedingungsart.BERUF, 1L)).bedingungen()
                .get(Bedingungsart.BERUF));
    }

    @Test
    @DisplayName("Der Schluessel wird normiert und auf das Muster geprueft")
    void schluesselNormiert() {
        assertEquals("kuerbishut",
                new Muenzwerte.Posten("  KuerbisHut ", Map.of("carved_pumpkin", 1), 2L,
                        Map.of()).schluessel());
        assertThrows(IllegalArgumentException.class,
                () -> new Muenzwerte.Posten("mein hut", Map.of("carved_pumpkin", 1), 2L,
                        Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Muenzwerte.Posten("2hut", Map.of("carved_pumpkin", 1), 2L,
                        Map.of()));
        assertFalse(Muenzwerte.brauchbarerSchluessel("x".repeat(
                Muenzwerte.HOECHSTE_SCHLUESSELLAENGE + 1)));
        assertTrue(Muenzwerte.brauchbarerSchluessel("x".repeat(
                Muenzwerte.HOECHSTE_SCHLUESSELLAENGE)));
    }

    @Test
    @DisplayName("Derselbe Posten zweimal kommt nicht zustande")
    void doppelterPosten() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Muenzwerte(true, List.of(hut(2L), hut(5L))));

        assertTrue(fehler.getMessage().contains("zweimal"), fehler.getMessage());
    }

    @Test
    @DisplayName("Mehr als 64 Posten kommen nicht zustande")
    void zuVielePosten() {
        List<Muenzwerte.Posten> zuViele = new ArrayList<>();
        for (int nummer = 0; nummer <= Muenzwerte.HOECHSTE_POSTENZAHL; nummer++) {
            zuViele.add(new Muenzwerte.Posten("posten-" + nummer,
                    Map.of("carved_pumpkin", 1), 2L, Map.of()));
        }

        assertThrows(IllegalArgumentException.class, () -> new Muenzwerte(true, zuViele));
        assertEquals(Muenzwerte.HOECHSTE_POSTENZAHL,
                new Muenzwerte(true, zuViele.subList(0,
                        Muenzwerte.HOECHSTE_POSTENZAHL)).katalog().size());
    }

    @Test
    @DisplayName("Ein leerer Katalog ist erlaubt und greift nicht")
    void leererKatalog() {
        Muenzwerte leer = new Muenzwerte(true, List.of());

        assertFalse(leer.greift(), "ein leerer Katalog verkauft nichts");
        assertTrue(leer.an(), "der Schalter steht trotzdem auf an - das sind zwei Dinge");
        assertFalse(Muenzwerte.VORGABE.mitAn(false).greift());
        assertThrows(IllegalArgumentException.class, () -> new Muenzwerte(true, null));
    }

    @Test
    @DisplayName("Gefragt wird nur nach den Arten, die wirklich vorkommen")
    void gebrauchtNurDasVerlangte() {
        // Dieselbe Zusage wie bei Rangregel.gebraucht: Eine Abfrage, deren Ergebnis
        // niemand liest, ist eine Fehlerquelle ohne Nutzen - sie kann werfen, und zwar
        // im Hauptfaden.
        assertEquals(List.of(Bedingungsart.TAETIGE_STUNDEN, Bedingungsart.PARZELLEN),
                Muenzwerte.VORGABE.gebraucht(),
                "in der Reihenfolge der Aufzaehlung, nicht in der der Datei");
        assertEquals(List.of(), new Muenzwerte(true, List.of(hut(2L))).gebraucht());
    }

    @Test
    @DisplayName("Der Posten zu einem Schluessel, gleich wie getippt")
    void postenZumSchluessel() {
        assertTrue(Muenzwerte.VORGABE.posten("  KUERBISHUT ").isPresent());
        assertTrue(Muenzwerte.VORGABE.posten("gibt-es-nicht").isEmpty());
        assertTrue(Muenzwerte.VORGABE.posten(null).isEmpty());
        assertEquals(List.of("kuerbishut", "blumenkiste", "musikscheibe",
                "rathausglocke"), Muenzwerte.VORGABE.schluessel());
    }

    @Test
    @DisplayName("Der teuerste und der guenstigste Posten - fuer die Rechenprobe")
    void teuersterUndGuenstigster() {
        assertEquals("musikscheibe", Muenzwerte.VORGABE.teuerster().orElseThrow()
                .schluessel(), "bei gleichem Preis der ERSTE der Datei");
        assertEquals("kuerbishut", Muenzwerte.VORGABE.guenstigster().orElseThrow()
                .schluessel());
        assertTrue(new Muenzwerte(true, List.of()).teuerster().isEmpty());
    }

    @Test
    @DisplayName("Die Stueckzahl eines Postens ist die Summe seiner Zeilen")
    void stueckzahlIstDieSumme() {
        assertEquals(48, Muenzwerte.VORGABE.posten("blumenkiste").orElseThrow()
                .stueckzahl(), "16 plus 16 plus 16");
        assertEquals(1, Muenzwerte.VORGABE.posten("kuerbishut").orElseThrow()
                .stueckzahl());
    }

    @Test
    @DisplayName("Ohne Bedingung heisst: fuer jeden")
    void ohneBedingung() {
        assertTrue(Muenzwerte.VORGABE.posten("kuerbishut").orElseThrow().ohneBedingung());
        assertFalse(Muenzwerte.VORGABE.posten("blumenkiste").orElseThrow()
                .ohneBedingung());
    }

    @Test
    @DisplayName("Zwei Zeilen, die zusammen in keinen Rucksack passen, kommen nicht durch")
    void summeUeberDerGrenze() {
        // BEFUND DER PRUEFRUNDE (20.11.2026): Geprueft wurde die ZEILE, nicht der Posten.
        // Zwei Zeilen von je 2.000 Stueck bestehen jede fuer sich und brauchen zusammen
        // 63 Lagerplaetze - der Posten stuende mit Preis in der Tafel und antwortete auch
        // bei voellig leerem Rucksack "dir fehlt Platz".
        Map<String, Integer> zweiZeilen = new LinkedHashMap<>();
        zweiZeilen.put("stone", 2_000);
        zweiZeilen.put("dirt", 2_000);

        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Muenzwerte.Posten("zuviel", zweiZeilen, 1L, Map.of()));

        assertTrue(fehler.getMessage().contains("4000"), fehler.getMessage());
        assertTrue(fehler.getMessage().contains(String.valueOf(
                Muenzwerte.HOECHSTE_STUECKZAHL)), fehler.getMessage());
    }

    @Test
    @DisplayName("Die Summe GENAU auf der Grenze geht durch")
    void summeAufDerGrenze() {
        // Die Gegenrichtung, und sie ist Pflicht: Mit > statt >= waere die Probe darueber
        // genauso gruen, und ein Posten auf genau 2.304 Stueck fiele mit einer Klage
        // heraus, die sich selbst widerspricht.
        Map<String, Integer> genau = new LinkedHashMap<>();
        genau.put("stone", Muenzwerte.HOECHSTE_STUECKZAHL / 2);
        genau.put("dirt", Muenzwerte.HOECHSTE_STUECKZAHL / 2);

        var posten = new Muenzwerte.Posten("genau", genau, 1L, Map.of());

        assertEquals(Muenzwerte.HOECHSTE_STUECKZAHL, posten.stueckzahl());
    }

    @Test
    @DisplayName("Die Stueckgrenze ist die Zahl der Lagerplaetze mal die Stapelgroesse")
    void grenzeIstDerRucksack() {
        // Die Zahl steht nicht als Literal da, sondern als Rechnung - und diese Probe
        // haelt sie an den Grund: 36 Plaetze mal 64 Stueck. Wer die Plaetze aendert,
        // aendert die Grenze mit.
        assertEquals(36, Muenzwerte.LAGERPLAETZE);
        assertEquals(Muenzwerte.LAGERPLAETZE * 64, Muenzwerte.HOECHSTE_STUECKZAHL);
    }
}
