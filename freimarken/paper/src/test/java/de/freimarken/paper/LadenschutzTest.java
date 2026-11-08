package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Mietvertrag;
import de.freimarken.kern.Quader;
import de.freimarken.kern.SpeicherMiete;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Ladenschutz - nur der Mieter darf in seinem Laden.
 *
 * <p>Die Probe zu der Luecke, die seit Teil A offen stand: Die Ladeneinheiten waren reine
 * Geometrie, jeder konnte in jedem Stand bauen und jede Ladentruhe leerraeumen. Die
 * {@link Schutzwache} gilt nur fuer die Marktstadt, und Altfurt ist eine andere Welt -
 * damit war das Mieten eine Buchung ohne Wirkung.
 *
 * <p>Geprueft wird die REGEL, nicht die fuenfzehn Ereignisarten der Wache. Dass jede von
 * ihnen durch dieselbe Regel laeuft, ist die Zusage der Wache und steht in
 * {@link SchutzwacheTest}; dass die Regel das Richtige sagt, steht hier.
 *
 * <p>Die drei Lagen, die ein Spieler unterscheiden muss, haben darum je eine eigene
 * Probe - und je einen eigenen Handbalken: der eigene Laden, der fremde Laden, der freie
 * Stand. Eine vierte Lage kommt dazu, die keine Lage ist: die stumme Datenbank.
 */
@DisplayName("Der Ladenschutz")
class LadenschutzTest {

    private static final String ALTFURT = "freimarken:altfurt";

    private static final String MARKTSTADT = "freimarken:marktstadt";

    private static final UUID ANNA = Spielerprobe.kennungAus(1);

    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private static final LocalDate TAG = LocalDate.of(2026, 11, 8);

    /** Die Einheit halle-1: Quader 60..64 / 71..75 / 4..8, Truhe bei 62/71/6. */
    private static final Ladeneinheit HALLE_1 = new Ladeneinheit("halle-1",
            new Quader(60, 71, 4, 64, 75, 8), 62, 71, 6);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpeicherMiete ablage;
    private Spielerprobe anna;
    private Spielerprobe bert;
    private Logger protokoll;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
        Serverprobe.nenne(ANNA, "Anna");
        Serverprobe.nenne(BERT, "Bert");
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        ablage = new SpeicherMiete();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", ANNA);
        bert = new Spielerprobe("Bert", BERT);
    }

    // ============================================================ nicht zustaendig

    @Test
    @DisplayName("Eine andere WELT geht den Ladenschutz nichts an")
    void fremdeWeltIstNichtZustaendig() {
        // Die billigste der drei Fragen und darum die erste: Diese Regel laeuft bei JEDEM
        // Blockereignis des Servers, und in jeder anderen Welt soll sie nach einem
        // Zeichenkettenvergleich fertig sein - ohne Verzeichnis und ohne Datenbank.
        //
        // Dieselben Koordinaten wie die Einheit, nur in der Marktstadt: Waere die
        // Weltpruefung weg, saehe die Regel hier einen Laden.
        Ladenschutz schutz = schutz();

        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                schutz.pruefe(bert.spieler(), ort(MARKTSTADT, 62, 71, 6)));
    }

    @Test
    @DisplayName("In der Ladenwelt, aber NEBEN den Einheiten: nicht zustaendig")
    void nebenDerEinheitIstNichtZustaendig() {
        // Altfurt ist mehr als die Markthalle. Was ausserhalb der Quader liegt, regelt
        // etwas anderes (heute nichts) - diese Regel darf dort nicht nein sagen, sonst
        // waere die ganze Hauptstadt gesperrt.
        Ladenschutz schutz = schutz();

        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                schutz.pruefe(bert.spieler(), ort(ALTFURT, 59, 71, 6)),
                "einen Block vor dem Quader");
        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                schutz.pruefe(bert.spieler(), ort(ALTFURT, 65, 71, 6)),
                "einen Block dahinter");
        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                schutz.pruefe(bert.spieler(), ort(ALTFURT, 62, 70, 6)),
                "einen Block darunter - der Quader hat auch eine Hoehe");
    }

    // ============================================================ die drei Lagen

    @Test
    @DisplayName("Der MIETER darf in seinem Laden")
    void derMieterDarf() {
        ablage.schreibe(vertrag(ANNA));
        Ladenschutz schutz = schutz();

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                schutz.pruefe(anna.spieler(), ort(ALTFURT, 62, 73, 6)));
    }

    @Test
    @DisplayName("Die TRUHE des Mieters ist seine - und nur seine")
    void dieTruheGehoertDemMieter() {
        // Der eigentliche Punkt des ganzen Schutzes: Ohne ihn konnte jeder das
        // Warenlager eines fremden Ladens leerraeumen. Die Truhenstelle wird eigens
        // geprueft, weil sie die ist, auf die es ankommt - und weil sie am Rand des
        // Quaders liegen kann.
        ablage.schreibe(vertrag(ANNA));
        Ladenschutz schutz = schutz();
        Location truhe = ort(ALTFURT, HALLE_1.truheX(), HALLE_1.truheY(),
                HALLE_1.truheZ());

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                schutz.pruefe(anna.spieler(), truhe));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                schutz.pruefe(bert.spieler(), truhe));
    }

    @Test
    @DisplayName("Ein FREMDER darf nicht - und der Handbalken nennt Stand und Mieter")
    void derFremdeDarfNicht() {
        // BEIDE Werte im Balken: In einer Markthalle stehen die Staende nebeneinander,
        // und wer nur "der Laden von Anna" liest, raet, an welchem er gerade greift.
        ablage.schreibe(vertrag(ANNA));
        Ladenschutz schutz = schutz();
        Location drin = ort(ALTFURT, 62, 73, 6);

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                schutz.pruefe(bert.spieler(), drin));

        String balken = Sprachprobe.bild(schutz.grund(drin));
        assertTrue(balken.contains("halle-1"), balken);
        assertTrue(balken.contains("Anna"), balken);
    }

    @Test
    @DisplayName("Ein FREIER Stand ist fuer JEDEN gesperrt")
    void derFreieStandIstGesperrt() {
        // Die Zeile, die nicht offensichtlich ist: Eine freie Einheit gehoert niemandem
        // und damit auch nicht jedem. Der Quader ist von Hand gebaute Einrichtung des
        // Betreibers; was ein Spieler dort hinterlaesst, hinterlaesst er dem naechsten
        // Mieter.
        //
        // Darauf stuetzt sich ausserdem die Reihenfolge der Raeumung in Mietlauf: Bricht
        // der Server zwischen dem Loeschen des Vertrags und dem Leeren der Truhe ab,
        // steht die Ware im Fach UND in der Truhe - aber an eine Truhe ohne Vertrag
        // kommt niemand heran.
        Ladenschutz schutz = schutz();
        Location drin = ort(ALTFURT, 62, 73, 6);

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                schutz.pruefe(anna.spieler(), drin));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                schutz.pruefe(bert.spieler(), drin));

        String balken = Sprachprobe.bild(schutz.grund(drin));
        assertTrue(balken.contains("frei"), balken);
        // Der WEG steht darin: Eine Absage ohne Ausweg liest sich wie ein Fehler.
        assertTrue(balken.contains("/laden mieten"), balken);
    }

    // ============================================================ die vierte Lage

    @Test
    @DisplayName("Schweigt die Datenbank, bleibt der Laden ZU - und sagt es")
    void stummeAblageSperrtUndSagtEs() {
        // Die sichere Richtung, dieselbe wie bei Schutzwache.darfHandeln: Ein stilles Ja
        // waere eine Markthalle, die bei jeder Netzstoerung offen steht - und ein
        // leergeraeumtes Warenlager bekommt niemand zurueck.
        Ladenschutz schutz = new Ladenschutz(verzeichnis(), new NichtLesbar(), ALTFURT,
                Sprachprobe.spracheAusDemJar(), protokoll);
        Location drin = ort(ALTFURT, 62, 73, 6);

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                schutz.pruefe(anna.spieler(), drin));

        // Ein EIGENER Balken: "frei" waere falsch und "gehoert X" erfunden. Der Spieler
        // soll wissen, dass er es spaeter noch einmal versuchen kann.
        String balken = Sprachprobe.bild(schutz.grund(drin));
        assertTrue(balken.contains("nicht nachsehbar"), balken);
        assertTrue(protokollzeilen.stream().anyMatch(zeile
                        -> zeile.getLevel() == Level.WARNING
                        && zeile.getMessage().contains("Der Laden bleibt zu")),
                protokollzeilen.toString());
    }

    @Test
    @DisplayName("Die Klage ist GEDROSSELT - ein toter Rechner fuellt kein Protokoll")
    void klageIstGedrosselt() {
        // Dieselbe gemessene Begruendung wie bei der Schutzwache: Diese Regel laeuft bei
        // jedem Blockereignis. Ohne Drossel schreibt ein ausgefallener Datenbankrechner
        // in Sekunden ein Protokoll voll und verdeckt jede andere Meldung.
        Ladenschutz schutz = new Ladenschutz(verzeichnis(), new NichtLesbar(), ALTFURT,
                Sprachprobe.spracheAusDemJar(), protokoll);
        Location drin = ort(ALTFURT, 62, 73, 6);

        for (int mal = 0; mal < 20; mal++) {
            schutz.pruefe(anna.spieler(), drin);
        }

        assertEquals(1, protokollzeilen.size(),
                "zwanzig Haende, EINE Zeile: " + protokollzeilen);
    }

    @Test
    @DisplayName("Ohne Spieler wird VERBOTEN - und nicht geworfen")
    void ohneSpielerVerboten() {
        // Ein null kommt hier nur aus einem Programmierfehler. Ein Wurf waere aber ein
        // Spieler, der gar nichts mehr tun kann: Diese Regel laeuft bei jedem
        // Blockereignis, und eine Ausnahme daraus beendet das Ereignis fuer alle.
        Ladenschutz schutz = schutz();
        ablage.schreibe(vertrag(ANNA));

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                schutz.pruefe(null, ort(ALTFURT, 62, 73, 6)));
        assertTrue(protokollzeilen.stream()
                        .anyMatch(zeile -> zeile.getLevel() == Level.WARNING),
                "und es steht im Protokoll: " + protokollzeilen);
    }

    @Test
    @DisplayName("Verschwindet die Einheit zwischen Urteil und Grund, bleibt der Balken")
    void einheitWegZwischenUrteilUndGrund() {
        // Ein Neuladen kann die Einheiten tauschen. Dann steht beim Nachfragen kein Laden
        // mehr da - und der allgemeine Satz ist die ehrlichere Auskunft als eine
        // erfundene Einheit oder ein Wurf mitten im Handbalken.
        Ladenschutz ohneEinheiten = new Ladenschutz(Ladenverzeichnis.ohneEinheiten(),
                ablage, ALTFURT, Sprachprobe.spracheAusDemJar(), protokoll);

        String balken = Sprachprobe.bild(ohneEinheiten.grund(ort(ALTFURT, 62, 73, 6)));

        assertTrue(balken.contains("frei"), balken);
    }

    @Test
    @DisplayName("Ohne Welt ist der Ladenschutz keine Regel")
    void ohneWeltKeineRegel() {
        // Ohne Weltschluessel suchte diese Regel in JEDER Welt nach Ladeneinheiten - und
        // sperrte dann in der Marktstadt einen Quader, der dort nicht steht.
        assertThrows(IllegalArgumentException.class, () -> new Ladenschutz(verzeichnis(),
                ablage, null, Sprachprobe.spracheAusDemJar(), protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Ladenschutz(verzeichnis(),
                ablage, "  ", Sprachprobe.spracheAusDemJar(), protokoll));
    }

    // ============================================================ Werkzeug

    private Ladenschutz schutz() {
        return new Ladenschutz(verzeichnis(), ablage, ALTFURT,
                Sprachprobe.spracheAusDemJar(), protokoll);
    }

    private static Ladenverzeichnis verzeichnis() {
        return Ladenverzeichnis.ausEinheiten(List.of(HALLE_1));
    }

    private static Mietvertrag vertrag(UUID wer) {
        return new Mietvertrag("halle-1", wer, TAG, TAG.plusDays(6), 500L, 5_000L);
    }

    private static Location ort(String weltschluessel, int x, int y, int z) {
        return new Location(Serverprobe.welt(weltschluessel), x, y, z);
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("LadenschutzProbe-" + UUID.randomUUID());
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

    /** Eine Mietablage, die nicht antwortet. */
    private static final class NichtLesbar implements Mietablage {
        @Override
        public Mietvertrag fuerEinheit(String einheit) {
            throw new IllegalStateException("nicht lesbar (Probe)");
        }

        @Override
        public Mietvertrag fuerMieter(UUID mieter) {
            throw new IllegalStateException("nicht lesbar (Probe)");
        }

        @Override
        public List<Mietvertrag> alle() {
            throw new IllegalStateException("nicht lesbar (Probe)");
        }

        @Override
        public void schreibe(Mietvertrag vertrag) {
            throw new IllegalStateException("nicht schreibbar (Probe)");
        }

        @Override
        public boolean loesche(String einheit) {
            throw new IllegalStateException("nicht loeschbar (Probe)");
        }
    }
}
