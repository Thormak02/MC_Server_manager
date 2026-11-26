package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Welche Weltregeln in welcher Welt greifen - und die zwei Sorten.
 *
 * <p>Die wichtigste Zusage dieser Datei ist {@link #zweiSortenUndJedeRegelHatIhre}: Jede
 * Regel muss sagen, ob sie WELTWEIT oder nur ueber Besitzgrenzen gilt. Eine sechste
 * Regel ohne diese Angabe landete in der Wache stumm im falschen Zweig - und ein stumm
 * nicht wirkender Schutz ist schlimmer als keiner, weil der Betreiber ihn eingetragen
 * hat.
 */
@DisplayName("Weltregelwerte")
class WeltregelwerteTest {

    private static final String MARKTSTADT = "freimarken:marktstadt";
    private static final String KORNMARK = "freimarken:kornmark";

    @Test
    @DisplayName("Ab Werk greift KEINE Regel - die offene Richtung, mit Absicht")
    void abWerkGreiftKeine() {
        // Die umgekehrte Richtung als beim Blockbuch, und das ist begruendet: Ein Buch,
        // das zu viel aufschreibt, kostet Platz; ein Weltschutz, der ungefragt ueberall
        // greift, nimmt dem Spieler das Spiel. In einer Farmwelt ist TNT das Werkzeug.
        assertTrue(Weltregelwerte.VORGABE.vorgabe().isEmpty());
        assertFalse(Weltregelwerte.VORGABE.greiftIrgendwo());
        for (Weltregel regel : Weltregel.values()) {
            assertFalse(Weltregelwerte.VORGABE.greift(MARKTSTADT, regel), regel.name());
        }
    }

    @Test
    @DisplayName("Zwei Sorten, und JEDE Regel hat ihre")
    void zweiSortenUndJedeRegelHatIhre() {
        // DIE ZUSAGE, AUF DIE ES ANKOMMT. Gefragt wird ueber die Aufzaehlung und nicht
        // mit fuenf abgeschriebenen Saetzen: Eine sechste Regel ohne Angabe waere sonst
        // gruen und landete in der Wache stumm im falschen Zweig.
        //
        // Dass BEIDE Sorten vorkommen, steht daneben - sonst waere ein "alles weltweit"
        // ebenfalls gruen, und die Grenzregeln verboten Kolben und Wasser ueberall.
        int weltweit = 0;
        int anGrenzen = 0;
        for (Weltregel regel : Weltregel.values()) {
            if (regel.giltWeltweit()) {
                weltweit++;
            } else {
                anGrenzen++;
            }
        }

        assertEquals(5, Weltregel.values().length,
                "fuenf Regeln waren es am 26.11.2026 - kommt eine dazu, gehoert sie in"
                        + " die config.yml, in die Wache und in diese Zahl");
        assertTrue(weltweit >= 1 && anGrenzen >= 1,
                "beide Sorten muessen vorkommen: " + weltweit + " weltweit, "
                        + anGrenzen + " an Grenzen");
        assertTrue(Weltregel.EXPLOSION.giltWeltweit(),
                "wer keine Krater will, will sie auch nicht auf einer freien Parzelle");
        assertFalse(Weltregel.KOLBEN.giltWeltweit(),
                "Redstone ist Spielinhalt - ganz zu verbieten naehme mehr, als es dem"
                        + " Nachbarn gibt");
    }

    @Test
    @DisplayName("Je Welt eine eigene Auswahl - und sie gilt STATT der Vorgabe")
    void jeWeltEigeneAuswahl() {
        Weltregelwerte werte = new Weltregelwerte(
                EnumSet.of(Weltregel.EXPLOSION),
                Map.of(KORNMARK, Set.of()));

        assertTrue(werte.greift(MARKTSTADT, Weltregel.EXPLOSION),
                "eine Welt ohne eigene Zeile folgt der Vorgabe");
        assertFalse(werte.greift(KORNMARK, Weltregel.EXPLOSION),
                "eine LEERE Liste heisst \"hier nicht\" und ist kein Rueckfall auf die"
                        + " Vorgabe");
    }

    @Test
    @DisplayName("Ein WeltNAME statt eines Schluessels wirft - der Wall gegen die Naht")
    void weltnameOhneNamensraumWirft() {
        // DERSELBE WALL wie bei Blockbuchwerte, und hier ist der Schaden groesser: Dort
        // bliebe ein Buch leer, hier bliebe eine Welt UNGESCHUETZT, obwohl sie
        // eingetragen ist. Nichts bricht, nichts wird rot - der Vergleich mit
        // World.key().asString() ist einfach immer ungleich.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Weltregelwerte(Set.of(),
                        Map.of("marktstadt", Set.of(Weltregel.EXPLOSION))));

        assertTrue(fehler.getMessage().contains("marktstadt"),
                "die Meldung muss den Namen nennen: " + fehler.getMessage());
        assertTrue(fehler.getMessage().contains("freimarken:marktstadt"),
                "und die erwartete Form zeigen: " + fehler.getMessage());
    }

    @Test
    @DisplayName("Eine LEERE Auswahl ist erlaubt - und EnumSet.copyOf waere der Wurf")
    void leereAuswahlIstErlaubt() {
        // Die Falle, die Blockbuchwerte.Auswahl schon einmal hatte: EnumSet.copyOf wirft
        // bei einer leeren Sammlung, die selbst kein EnumSet ist ("Collection is
        // empty"). Und die leere Auswahl ist hier der ERLAUBTE Fall - ein Wurf traefe
        // genau den Betreiber, der eine Welt bewusst ausnimmt.
        Weltregelwerte werte = new Weltregelwerte(Set.of(), Map.of(KORNMARK, Set.of()));

        assertTrue(werte.fuerWelt(KORNMARK).isEmpty());
        assertFalse(werte.greiftIrgendwo());
    }

    @Test
    @DisplayName("Die Auswahl ist unveraenderlich - auch die hereingegebene")
    void auswahlIstUnveraenderlich() {
        Set<Weltregel> roh = EnumSet.of(Weltregel.EXPLOSION);
        Weltregelwerte werte = new Weltregelwerte(roh, Map.of());

        roh.add(Weltregel.FEUER);

        assertFalse(werte.vorgabe().contains(Weltregel.FEUER),
                "eine Kopie und keine Sicht: Sonst aenderte der Aufrufer den Schutz,"
                        + " nachdem er ihn uebergeben hat");
        assertThrows(UnsupportedOperationException.class,
                () -> werte.vorgabe().add(Weltregel.FEUER));
    }

    @Test
    @DisplayName("Der Bericht nennt die Regeln in der Reihenfolge der Aufzaehlung")
    void berichtIstVergleichbar() {
        // Vergleichbar zwischen zwei Starts: Eine Zeile, die bei jedem Start anders
        // sortiert ist, laesst sich nicht gegen die von gestern halten.
        // LinkedHashSet und NICHT EnumSet: Ein EnumSet sortiert seine Mitglieder
        // selbst nach der Aufzaehlung, also waeren die zwei Mengen schon vor dem
        // Aufruf gleich - die Probe haette nichts zu pruefen und waere gruen, auch
        // wenn wieEingetragen die Reihenfolge der Eingabe uebernaehme.
        Set<Weltregel> rueckwaerts = new java.util.LinkedHashSet<>();
        rueckwaerts.add(Weltregel.FLUESSIGKEIT);
        rueckwaerts.add(Weltregel.EXPLOSION);
        Set<Weltregel> vorwaerts = new java.util.LinkedHashSet<>();
        vorwaerts.add(Weltregel.EXPLOSION);
        vorwaerts.add(Weltregel.FLUESSIGKEIT);
        assertEquals(java.util.List.of(Weltregel.FLUESSIGKEIT, Weltregel.EXPLOSION),
                java.util.List.copyOf(rueckwaerts),
                "die Eingabe muss WIRKLICH verdreht sein, sonst prueft der Vergleich"
                        + " unten nichts");

        String eine = Weltregelwerte.wieEingetragen(rueckwaerts);
        String andere = Weltregelwerte.wieEingetragen(vorwaerts);

        assertEquals(eine, andere);
        assertEquals("[explosion, fluessigkeit]", eine);
        assertEquals("[]", Weltregelwerte.wieEingetragen(Set.of()));
    }

    @Test
    @DisplayName("Jeder Name aus der Datei findet seine Regel - und zurueck")
    void namenGehenInBeideRichtungen() {
        for (Weltregel regel : Weltregel.values()) {
            assertEquals(regel, Weltregel.ausSchluessel(regel.schluessel()));
            assertEquals(regel, Weltregel.ausSchluessel(
                    regel.schluessel().toUpperCase(java.util.Locale.ROOT)),
                    "Gross- und Kleinschreibung darf keinen Unterschied machen - ein"
                            + " Betreiber, der EXPLOSION tippt, hat sich nicht vertippt");
            assertTrue(Weltregel.erlaubteNamen().contains(regel.schluessel()),
                    "die Klage muss JEDEN Namen nennen koennen: " + regel);
        }
        assertEquals(Weltregel.values().length, Weltregel.erlaubteNamen().size());
        assertEquals(null, Weltregel.ausSchluessel("gibtsnicht"));
        assertEquals(null, Weltregel.ausSchluessel(null));
    }

    @Test
    @DisplayName("Nichts statt einer Vorgabe oder einer Karte wirft")
    void nichtsWirft() {
        assertThrows(IllegalArgumentException.class,
                () -> new Weltregelwerte(null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Weltregelwerte(Set.of(), null));
        // Eine Welt MIT Namen, aber ohne Auswahl: Fuer "nichts" steht die leere Liste.
        var karte = new java.util.HashMap<String, Set<Weltregel>>();
        karte.put(MARKTSTADT, null);
        assertThrows(IllegalArgumentException.class,
                () -> new Weltregelwerte(Set.of(), karte));
    }

    @Test
    @DisplayName("Eine unbekannte Welt bekommt die Vorgabe, nicht eine leere Antwort")
    void unbekannteWeltBekommtDieVorgabe() {
        // Der Unterschied zaehlt: Mit einer leeren Antwort waere eine neu angelegte Welt
        // ungeschuetzt, obwohl der Betreiber eine Vorgabe eingetragen hat.
        Weltregelwerte werte = new Weltregelwerte(
                EnumSet.of(Weltregel.EXPLOSION), Map.of(KORNMARK, Set.of()));

        assertTrue(werte.greift("freimarken:gibtsnochnicht", Weltregel.EXPLOSION));
        assertTrue(werte.fuerWelt(null).contains(Weltregel.EXPLOSION),
                "auch ohne Welt die Vorgabe - ein null kommt von einer entladenen Welt");
        assertFalse(werte.greift(MARKTSTADT, null),
                "ohne Regel greift nichts, und das wirft nicht");
    }
}
