package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Abholablage;
import de.freimarken.kern.Abholgut;
import de.freimarken.kern.Abholstueck;
import de.freimarken.kern.SpeicherAbholfach;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Abholfach als Naht - holen, zuruecklegen, liegen lassen.
 *
 * <p>Ohne Server: Das Inventar kommt nur ueber die Attrappe {@link Probenzusteller}
 * herein, und die zaehlt mit, was sie bekommen hat. Darauf laufen die beiden
 * Reihenfolgeproben - "erst loeschen, dann geben" ist die Zusage, die keine Gegenstaende
 * erzeugen kann, und "lesbar fragen, dann loeschen" die, die keine verliert.
 */
@DisplayName("Das Abholfach")
class AbholbuchTest {

    private static final UUID ANNA =
            UUID.fromString("0000a44a-0000-0000-0000-000000000001");

    private static final UUID BERT =
            UUID.fromString("0000be27-0000-0000-0000-000000000002");

    private static final Clock UHR =
            Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpeicherAbholfach fach;
    private Probenzusteller zusteller;
    private Logger protokoll;

    @BeforeEach
    void vorher() {
        fach = new SpeicherAbholfach();
        zusteller = new Probenzusteller();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
    }

    @Test
    @DisplayName("Ein leeres Fach ist FACH_LEER und kein Fehler")
    void leeresFach() {
        Abholbuch.Griff griff = buch().hole(ANNA, zusteller, 36);

        assertEquals(Abholbuch.Bescheid.FACH_LEER, griff.bescheid());
        assertEquals(0, griff.geholt());
        assertEquals(0, griff.bleibt());
        assertEquals(0, zusteller.gegeben.size());
    }

    @Test
    @DisplayName("Was im Fach liegt, kommt heraus - und ist DANN weg")
    void holenGibtUndLoescht() {
        lege(ANNA, "64x STONE", "12x OAK_LOG");

        Abholbuch.Griff griff = buch().hole(ANNA, zusteller, 36);

        assertEquals(Abholbuch.Bescheid.GEHOLT, griff.bescheid());
        assertEquals(2, griff.geholt());
        assertEquals(0, griff.bleibt());
        assertEquals(List.of("inhalt-von-64x STONE", "inhalt-von-12x OAK_LOG"),
                zusteller.gegeben);
        assertEquals(0, fach.anzahlFuer(ANNA), "das Fach ist leer");
    }

    @Test
    @DisplayName("Geloescht wird VOR dem Geben - Stapel fuer Stapel")
    void erstLoeschenDannGeben() {
        // Die Reihenfolge, die keine Gegenstaende erzeugen kann (dieselbe Richtung wie
        // beim Kuendigen in Mietbuch). Die Attrappe sieht beim Geben nach, ob die Zeile
        // schon weg ist: Waere sie noch da, haette ein Abbruch genau dort die Ware im
        // Inventar UND in der Datenbank - und ein verdoppelter Diamantblock ist in einer
        // Wirtschaft mit Bundesmarkt sofort Geld.
        lege(ANNA, "64x STONE");
        zusteller.schauBeimGeben(() -> fach.anzahlFuer(ANNA));

        buch().hole(ANNA, zusteller, 36);

        assertEquals(List.of(0), zusteller.standBeimGeben,
                "beim Geben lag die Zeile schon nicht mehr im Fach");
    }

    @Test
    @DisplayName("Was NICHT geloescht wurde, wird auch NICHT gegeben")
    void nurGeloeschtesWirdGegeben() {
        // Die wichtigste der dreizehn durchgelassenen Einspritzungen vom 08.11.2026 -
        // und die eine, die Gegenstaende ERZEUGT: Der Zweig "schon weg, also weiter"
        // liess sich wegnehmen, ohne dass eine Probe rot wurde. Dann gibt das Abholbuch
        // einen Stapel heraus, dessen Zeile ein zweiter Befehl gerade geholt hat - und
        // beide Spieler haben ihn.
        //
        // Nachgestellt mit einem Fach, dessen nimm immer 0 meldet: genau das Bild, das
        // ein Wettlauf zweier Fenster hinterlaesst.
        lege(ANNA, "eins", "zwei");

        Abholbuch.Griff griff = new Abholbuch(new SchonWeg(fach), UHR, protokoll)
                .hole(ANNA, zusteller, 36);

        assertEquals(List.of(), zusteller.gegeben,
                "kein Stapel darf gegeben werden, wenn keine Zeile geloescht wurde");
        assertEquals(0, griff.geholt());
        assertEquals(2, griff.bleibt());
    }

    @Test
    @DisplayName("Mehr Stapel als Plaetze: der Rest BLEIBT liegen")
    void nurSoVieleWiePlaetze() {
        lege(ANNA, "eins", "zwei", "drei");

        Abholbuch.Griff griff = buch().hole(ANNA, zusteller, 2);

        assertEquals(Abholbuch.Bescheid.GEHOLT, griff.bescheid());
        assertEquals(2, griff.geholt());
        assertEquals(1, griff.bleibt());
        // Das AELTESTE zuerst - wer sein Fach in mehreren Griffen leert, soll nicht
        // zweimal dasselbe angezeigt bekommen.
        assertEquals(List.of("inhalt-von-eins", "inhalt-von-zwei"), zusteller.gegeben);
        assertEquals(List.of("drei"), beschriftungen(ANNA));
    }

    @Test
    @DisplayName("Kein Platz: KEIN_PLATZ, und es ist NICHTS geloescht")
    void keinPlatz() {
        lege(ANNA, "eins", "zwei");

        Abholbuch.Griff griff = buch().hole(ANNA, zusteller, 0);

        assertEquals(Abholbuch.Bescheid.KEIN_PLATZ, griff.bescheid());
        assertEquals(0, griff.geholt());
        assertEquals(2, griff.bleibt());
        assertEquals(2, fach.anzahlFuer(ANNA), "nichts darf weg sein");
    }

    @Test
    @DisplayName("Ein leeres Fach OHNE Platz meldet FACH_LEER, nicht KEIN_PLATZ")
    void leerSchlaegtKeinPlatz() {
        // "Dein Abholfach ist leer" ist die bessere Auskunft, wenn beides zutrifft: Wer
        // nichts da hat, braucht nicht erst Platz zu machen.
        Abholbuch.Griff griff = buch().hole(ANNA, zusteller, 0);

        assertEquals(Abholbuch.Bescheid.FACH_LEER, griff.bescheid());
    }

    @Test
    @DisplayName("Ein unlesbarer Stapel wird UEBERGANGEN und bleibt liegen")
    void unlesbarBleibtLiegen() {
        // Das Versprechen des Fachs ist "wird nie geloescht". Ein Stapel, den ein
        // Minecraft-Update unlesbar gemacht hat, darf darum nicht verschwinden - er
        // bleibt da und steht im Protokoll, MIT Beschriftung, damit ein Betreiber ihn von
        // Hand ersetzen kann.
        lege(ANNA, "kaputt", "gut");
        zusteller.unlesbar("inhalt-von-kaputt");

        Abholbuch.Griff griff = buch().hole(ANNA, zusteller, 36);

        assertEquals(Abholbuch.Bescheid.GEHOLT, griff.bescheid());
        assertEquals(1, griff.geholt());
        assertEquals(1, griff.unlesbar());
        assertEquals(1, griff.bleibt());
        assertEquals(List.of("kaputt"), beschriftungen(ANNA));
        assertTrue(hatZeile(Level.WARNING, "\"kaputt\""), zeilenText());
    }

    @Test
    @DisplayName("Ist ALLES unlesbar, ist der Bescheid GESTOERT")
    void allesUnlesbar() {
        // Nicht FACH_LEER: Es liegt etwas da, es geht nur nicht heraus. Der Spieler soll
        // es erfahren und nicht raten, warum sein Fach nicht leer wird.
        lege(ANNA, "kaputt");
        zusteller.unlesbar("inhalt-von-kaputt");

        Abholbuch.Griff griff = buch().hole(ANNA, zusteller, 36);

        assertEquals(Abholbuch.Bescheid.GESTOERT, griff.bescheid());
        assertEquals(0, griff.geholt());
        assertEquals(1, griff.unlesbar());
        assertEquals(1, griff.bleibt());
    }

    @Test
    @DisplayName("Passt ein Stapel doch nicht, wird er ZURUECKGELEGT")
    void nichtZustellbarKommtZurueck() {
        // Der eine Augenblick, in dem ein Gegenstand nirgends ist: aus dem Fach genommen
        // und nicht im Inventar. Zurueck damit, und zwar sofort - sonst ist er verloren.
        lege(ANNA, "eins", "zwei");
        zusteller.passtNicht("inhalt-von-eins");

        Abholbuch.Griff griff = buch().hole(ANNA, zusteller, 36);

        assertEquals(0, griff.geholt());
        // ZWEI, und das ist der Teil, den diese Probe beim ersten Schreiben falsch
        // erwartet hatte: Der Griff bricht beim ersten "passt nicht" AB und laesst
        // "zwei" unberuehrt liegen. Das ist richtig - wenn ein Stapel nicht mehr ins
        // Inventar geht, geht der naechste auch nicht, und jeder weitere Versuch waere
        // ein weiterer Augenblick, in dem ein Gegenstand nirgends ist.
        assertEquals(2, griff.bleibt());
        // Die REIHENFOLGE sagt, welcher von beiden zurueckgelegt wurde: "eins" steht
        // jetzt HINTEN, weil es eine neue Nummer bekommen hat. Das ist hingenommen - die
        // Reihenfolge im Fach ist eine Bequemlichkeit, der Gegenstand ist es nicht.
        assertEquals(List.of("zwei", "eins"), beschriftungen(ANNA));
        assertTrue(hatZeile(Level.INFO, "liegt wieder"), zeilenText());
    }

    @Test
    @DisplayName("Laesst sich das Zuruecklegen nicht, steht es als SEVERE da")
    void verlorenerStapelWirdGemeldet() {
        // Der EINZIGE Weg, auf dem dieses Teil einen Gegenstand verlieren kann - genannt
        // statt verschwiegen. Beschriftung UND Herkunft stehen in der Zeile, damit ein
        // Betreiber ihn von Hand ersetzen kann.
        lege(ANNA, "eins");
        zusteller.passtNicht("inhalt-von-eins");
        Abholablage einbahn = new NurNehmen(fach);

        new Abholbuch(einbahn, UHR, protokoll).hole(ANNA, zusteller, 36);

        assertTrue(hatZeile(Level.SEVERE, "VERLOREN"), zeilenText());
        assertTrue(hatZeile(Level.SEVERE, "\"eins\""), zeilenText());
    }

    @Test
    @DisplayName("Ein FREMDES Fach wird nicht angefasst")
    void fremdesFachBleibtLiegen() {
        lege(ANNA, "Annas Stein");
        lege(BERT, "Berts Holz");

        buch().hole(ANNA, zusteller, 36);

        assertEquals(List.of("Berts Holz"), beschriftungen(BERT));
        assertEquals(List.of("inhalt-von-Annas Stein"), zusteller.gegeben);
    }

    @Test
    @DisplayName("stand zaehlt, was da ist - und 0 bei einer Stoerung")
    void standZaehlt() {
        lege(ANNA, "eins", "zwei");

        assertEquals(2, buch().stand(ANNA));
        assertEquals(0, buch().stand(BERT));
        assertEquals(0, buch().stand(null), "kein Spieler, kein Fach");

        assertEquals(0, new Abholbuch(new NichtLesbar(), UHR, protokoll).stand(ANNA));
        assertTrue(hatZeile(Level.WARNING, "nicht zaehlbar"), zeilenText());
    }

    @Test
    @DisplayName("Eine Ablage, die nicht lesen kann, meldet GESTOERT und wirft nicht")
    void nichtsWirftNachAussen() {
        Abholbuch buch = new Abholbuch(new NichtLesbar(), UHR, protokoll);

        Abholbuch.Griff griff = buch.hole(ANNA, zusteller, 36);

        assertEquals(Abholbuch.Bescheid.GESTOERT, griff.bescheid());
        assertEquals(0, griff.geholt());
        assertTrue(hatZeile(Level.WARNING, "nicht lesbar"), zeilenText());
    }

    @Test
    @DisplayName("Ohne Spieler antwortet der Griff mit FACH_LEER")
    void ohneSpieler() {
        assertEquals(Abholbuch.Bescheid.FACH_LEER,
                buch().hole(null, zusteller, 36).bescheid());
    }

    // ==================================================================== Werkzeug

    private Abholbuch buch() {
        return new Abholbuch(fach, UHR, protokoll);
    }

    private void lege(UUID wer, String... beschriftungen) {
        List<Abholgut> gut = new ArrayList<>();
        for (String name : beschriftungen) {
            gut.add(new Abholgut(name, "inhalt-von-" + name));
        }
        fach.lege(wer, "Laden halle-1", UHR.instant(), gut);
    }

    private List<String> beschriftungen(UUID wer) {
        List<String> namen = new ArrayList<>();
        for (Abholstueck stueck : fach.fuerSpieler(wer)) {
            namen.add(stueck.beschriftung());
        }
        return namen;
    }

    private boolean hatZeile(Level stufe, String teil) {
        return protokollzeilen.stream().anyMatch(zeile -> zeile.getLevel() == stufe
                && zeile.getMessage() != null && zeile.getMessage().contains(teil));
    }

    private String zeilenText() {
        StringBuilder alles = new StringBuilder();
        for (LogRecord zeile : protokollzeilen) {
            alles.append(zeile.getLevel()).append(": ").append(zeile.getMessage())
                    .append(" | ");
        }
        return alles.toString();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("AbholprobeLog-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokollzeilen.add(satz);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return eigener;
    }

    /** Ein Inventar im Arbeitsspeicher, das mitschreibt, was es bekommen hat. */
    private static final class Probenzusteller implements Abholbuch.Zusteller {

        private final List<String> gegeben = new ArrayList<>();
        private final List<Integer> standBeimGeben = new ArrayList<>();
        private final List<String> nichtLesbar = new ArrayList<>();
        private final List<String> passtNicht = new ArrayList<>();
        private java.util.function.IntSupplier standGeber;

        void unlesbar(String inhalt) {
            nichtLesbar.add(inhalt);
        }

        void passtNicht(String inhalt) {
            passtNicht.add(inhalt);
        }

        /** Von jetzt an wird bei jedem Geben der Stand des Fachs aufgeschrieben. */
        void schauBeimGeben(java.util.function.IntSupplier geber) {
            this.standGeber = geber;
        }

        @Override
        public boolean lesbar(String inhalt) {
            return !nichtLesbar.contains(inhalt);
        }

        @Override
        public boolean gib(String inhalt) {
            if (standGeber != null) {
                standBeimGeben.add(standGeber.getAsInt());
            }
            if (passtNicht.contains(inhalt)) {
                return false;
            }
            gegeben.add(inhalt);
            return true;
        }
    }

    /** Ein Fach, aus dem man nehmen, in das man aber nichts zuruecklegen kann. */
    private static final class NurNehmen implements Abholablage {

        private final Abholablage echte;

        NurNehmen(Abholablage echte) {
            this.echte = echte;
        }

        @Override
        public List<Abholstueck> fuerSpieler(UUID empfaenger) {
            return echte.fuerSpieler(empfaenger);
        }

        @Override
        public int anzahlFuer(UUID empfaenger) {
            return echte.anzahlFuer(empfaenger);
        }

        @Override
        public void lege(UUID empfaenger, String herkunft, Instant wann,
                List<Abholgut> stapel) {
            throw new IllegalStateException("Abholfach nicht schreibbar (Probe)");
        }

        @Override
        public int nimm(UUID empfaenger, List<Long> ids) {
            return echte.nimm(empfaenger, ids);
        }
    }

    /**
     * Ein Fach, dessen nimm immer 0 meldet - das Bild eines verlorenen Wettlaufs.
     *
     * <p>Gelesen wird aus dem echten Fach, geloescht wird nie. Genau so sieht es aus,
     * wenn ein zweiter Befehl zwischen Lesen und Loeschen dieselben Stuecke geholt hat.
     */
    private static final class SchonWeg implements Abholablage {

        private final Abholablage echte;

        SchonWeg(Abholablage echte) {
            this.echte = echte;
        }

        @Override
        public List<Abholstueck> fuerSpieler(UUID empfaenger) {
            return echte.fuerSpieler(empfaenger);
        }

        @Override
        public int anzahlFuer(UUID empfaenger) {
            return echte.anzahlFuer(empfaenger);
        }

        @Override
        public void lege(UUID empfaenger, String herkunft, Instant wann,
                List<Abholgut> stapel) {
            echte.lege(empfaenger, herkunft, wann, stapel);
        }

        @Override
        public int nimm(UUID empfaenger, List<Long> ids) {
            return 0;
        }
    }

    /** Ein Fach, das nicht antwortet. */
    private static final class NichtLesbar implements Abholablage {
        @Override
        public List<Abholstueck> fuerSpieler(UUID empfaenger) {
            throw new IllegalStateException("nicht lesbar (Probe)");
        }

        @Override
        public int anzahlFuer(UUID empfaenger) {
            throw new IllegalStateException("nicht zaehlbar (Probe)");
        }

        @Override
        public void lege(UUID empfaenger, String herkunft, Instant wann,
                List<Abholgut> stapel) {
            throw new IllegalStateException("nicht schreibbar (Probe)");
        }

        @Override
        public int nimm(UUID empfaenger, List<Long> ids) {
            throw new IllegalStateException("nicht loeschbar (Probe)");
        }
    }
}
