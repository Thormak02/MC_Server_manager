package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Abholgut;
import de.freimarken.kern.Mietvertrag;
import de.freimarken.kern.Mietwerte;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.SpeicherAbholfach;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherMiete;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Bruecke zwischen dem Spiel und dem {@link Mietlauf} - Beitritt und Takt.
 *
 * <p><b>Diese Datei muss es geben, und das ist ein Befund der Einspritzungskampagne vom
 * 08.11.2026.</b> Vier Eingriffe liefen gruen durch, weil die VERDRAHTUNG ungeprueft war:
 * Der Beitritt sagte nichts ueber den Verzug, nichts ueber das Abholfach, und der Lauf
 * schickte seine Zeilen an den erstbesten anwesenden Spieler statt an den Mieter. Jede
 * dieser Zeilen ist einzeln in {@code MiettextTest} geprueft - aber ob sie jemals
 * jemanden erreicht, prueft nur eine Probe der Wache.
 *
 * <p>Der dritte Eingriff ist der gefaehrlichste: Die Zeilen des Laufs gehen an GELD. Wer
 * die Warnung eines anderen liest, haelt seinen eigenen Laden fuer in Verzug - und wer
 * seine eigene nie bekommt, verliert ihn ohne Vorwarnung.
 */
@DisplayName("Die Mietwache")
class MietwacheTest {

    private static final String ALTFURT = "freimarken:altfurt";

    private static final UUID ANNA = Spielerprobe.kennungAus(1);

    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private static final Instant START = Instant.parse("2026-11-08T12:00:00Z");

    private static final LocalDate TAG = LocalDate.of(2026, 11, 8);

    private static final Clock UHR = Clock.fixed(START, ZoneOffset.UTC);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private Wirtschaftsstand wirtschaft;
    private SpeicherMiete ablage;
    private SpeicherAbholfach fach;
    private SpeicherKasse kasse;
    private Spielerprobe anna;
    private Spielerprobe bert;
    private Logger protokoll;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.niemandMehrDa();
        Serverprobe.keineWeltenMehr();
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                .mitLaeden(Mietwerte.VORGABE.mitDauer(30).mitAusfaellen(3)));
        ablage = new SpeicherMiete();
        fach = new SpeicherAbholfach();
        kasse = new SpeicherKasse(1_000_000L, UHR);
        kasse.eroeffne(ANNA);
        kasse.eroeffne(BERT);
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", ANNA).inWelt(Serverprobe.welt(ALTFURT));
        bert = new Spielerprobe("Bert", BERT).inWelt(Serverprobe.welt(ALTFURT));
    }

    // ==================================================================== Beitritt

    @Test
    @DisplayName("Der Beitritt sagt, dass der Laden in Verzug ist")
    void beitrittNenntDenVerzug() {
        // Die EINZIGE Stelle, an der ein Spieler von einem Ausfall erfaehrt, den er
        // verschlafen hat: Der Lauf laeuft, waehrend er offline ist. Ohne diese Zeile
        // kaeme er zurueck und faende seinen Laden weg, ohne je gewarnt worden zu sein.
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, 2));

        wache().beimBeitritt(beitritt(anna));

        assertEquals(1, anna.chatBild().size(), anna.chatBild().toString());
        assertTrue(anna.chatBild().get(0).contains("2 von 3"),
                anna.chatBild().get(0));
        assertTrue(anna.chatBild().get(0).contains("halle-1"), anna.chatBild().get(0));
    }

    @Test
    @DisplayName("Ohne Verzug und ohne Fach sagt der Beitritt NICHTS")
    void beitrittSchweigtImNormalfall() {
        // Der haeufigste Beitritt. Zwei Zeilen bei jedem Einloggen waeren Laerm, und
        // dann liest niemand mehr die, auf die es ankommt.
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L));

        wache().beimBeitritt(beitritt(anna));

        assertEquals(List.of(), anna.chatBild());
    }

    @Test
    @DisplayName("Der Beitritt nennt das Abholfach - auch OHNE Laden")
    void beitrittNenntDasFach() {
        // Gerade der Geraeumte hat keinen Vertrag mehr, und gerade er muss erfahren, wo
        // seine Ware liegt. Eine Zeile nur fuer Mieter traefe jeden ausser den
        // Betroffenen.
        fach.lege(ANNA, "Laden halle-1", START,
                List.of(new Abholgut("64x STONE", "x"),
                        new Abholgut("12x OAK_LOG", "y")));

        wache().beimBeitritt(beitritt(anna));

        assertEquals(1, anna.chatBild().size(), anna.chatBild().toString());
        assertTrue(anna.chatBild().get(0).contains("2 Stapel"), anna.chatBild().get(0));
    }

    @Test
    @DisplayName("Verzug UND Abholfach: der Verzug steht ZUERST")
    void verzugStehtVorDemFach() {
        // Der Verzug ist die Nachricht, bei der etwas zu tun ist; das Fach ist eine
        // Auskunft. Eine verdrehte Reihenfolge sieht keine der zwei Einzelproben.
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L, 1));
        fach.lege(ANNA, "Laden halle-1", START,
                List.of(new Abholgut("64x STONE", "x")));

        wache().beimBeitritt(beitritt(anna));

        List<String> bild = anna.chatBild();
        assertEquals(2, bild.size(), bild.toString());
        assertTrue(bild.get(0).contains("in Verzug"), bild.get(0));
        assertTrue(bild.get(1).contains("Abholfach"), bild.get(1));
    }

    // ==================================================================== Takt

    @Test
    @DisplayName("Der Lauf schickt seine Zeilen an SEINEN Mieter")
    void taktSchicktAnDenMieter() {
        // Der gefaehrlichste der vier durchgelassenen Eingriffe: Wer die Warnung eines
        // anderen liest, haelt seinen eigenen Laden fuer in Verzug - und wer seine eigene
        // nie bekommt, verliert ihn ohne Vorwarnung.
        //
        // Beide Spieler sind anwesend, und NUR Bert ist in Verzug. Waere hier der
        // erstbeste Anwesende gemeint, bekaeme Anna die Zeile.
        Serverprobe.anmelden(anna, "Anna");
        Serverprobe.anmelden(bert, "Bert");
        SpeicherKasse arm = new SpeicherKasse(100L, UHR);
        arm.eroeffne(BERT);
        ablage.schreibe(new Mietvertrag("halle-2", BERT, TAG, TAG.plusDays(29), 500L,
                5_000L));

        wache(arm).imTakt();

        assertEquals(List.of(), anna.chatBild(), "Anna hat keinen Laden");
        assertEquals(1, bert.chatBild().size(), bert.chatBild().toString());
        assertTrue(bert.chatBild().get(0).contains("halle-2"), bert.chatBild().get(0));
        assertTrue(bert.chatBild().get(0).contains("1 von 3"), bert.chatBild().get(0));
    }

    @Test
    @DisplayName("Wer bezahlt, liest nichts - der Takt bleibt still")
    void taktSchweigtBeiZahlung() {
        Serverprobe.anmelden(anna, "Anna");
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L));

        wache().imTakt();

        assertEquals(List.of(), anna.chatBild());
        // Gezahlt wurde aber: Die Stille ist keine Untaetigkeit.
        assertEquals(-500L, kasse.summeSeit(ANNA, Instant.EPOCH,
                de.freimarken.kern.Buchungsgrund.LADEN_MIETE));
    }

    @Test
    @DisplayName("Ein ABWESENDER Mieter haelt den Takt nicht auf")
    void abwesenderMieterIstKeinFehler() {
        // Niemand ist angemeldet, der Vertrag gehoert Anna. Das Geld muss trotzdem
        // fliessen - das ist der ganze Zweck des taeglichen Laufs.
        ablage.schreibe(new Mietvertrag("halle-1", ANNA, TAG, TAG.plusDays(29), 500L,
                5_000L));

        wache().imTakt();

        assertEquals(-500L, kasse.summeSeit(ANNA, Instant.EPOCH,
                de.freimarken.kern.Buchungsgrund.LADEN_MIETE));
        assertEquals(List.of(), anna.chatBild(), "sie war nicht da");
    }

    @Test
    @DisplayName("Ohne Vertraege tut der Takt nichts und wirft nicht")
    void leererTakt() {
        Serverprobe.anmelden(anna, "Anna");

        wache().imTakt();

        assertEquals(List.of(), anna.chatBild());
        assertTrue(protokollzeilen.isEmpty(), protokollzeilen.toString());
    }

    // ==================================================================== Werkzeug

    private Mietwache wache() {
        return wache(kasse);
    }

    private Mietwache wache(SpeicherKasse welche) {
        Ladenverzeichnis verzeichnis = Ladenverzeichnis.ausEinheiten(List.of(
                new Ladeneinheit("halle-1", new Quader(60, 71, 4, 64, 75, 8), 62, 71, 6),
                new Ladeneinheit("halle-2", new Quader(70, 71, 4, 74, 75, 8), 72, 71,
                        6)));
        Mietlauf lauf = new Mietlauf(wirtschaft, ablage, fach, welche, new LeereTruhe(),
                UHR, protokoll);
        Mietbuch buch = new Mietbuch(wirtschaft, verzeichnis, ablage, welche, new Probentruhe(), UHR,
                protokoll);
        Abholbuch abholbuch = new Abholbuch(fach, UHR, protokoll);
        return new Mietwache(Bukkit.getServer(), lauf, buch, abholbuch, wirtschaft,
                Sprachprobe.spracheAusDemJar());
    }

    private static PlayerJoinEvent beitritt(Spielerprobe wer) {
        return new PlayerJoinEvent(wer.spieler(), Component.empty());
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("MietwacheProbe-" + UUID.randomUUID());
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

    /** Eine Ladentruhe, in der nichts liegt - hier geht es nicht um die Ware. */
    private static final class LeereTruhe implements Mietlauf.Truhe {
        @Override
        public List<Abholgut> lese(String einheit) {
            return List.of();
        }

        @Override
        public void leere(String einheit) {
        }
    }
}
