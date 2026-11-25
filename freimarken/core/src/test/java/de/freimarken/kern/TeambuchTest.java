package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wer wem welche Teamrolle geben und nehmen darf.
 *
 * <p>Die tragende Regel ist der RECHTERIEGEL: Eine Rolle vergibt nur, wer jedes ihrer
 * Rechte selbst hat. Jede Probe, die ihn prueft, stellt den Zustand her, in dem er GREIFT
 * - ein Moderator, der Verwalter vergeben will. Eine Probe mit einem Vergebenden, der
 * ohnehin alles darf, waere gruen mit und ohne Riegel.
 */
@DisplayName("Teamrollen vergeben und nehmen")
class TeambuchTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-24T12:00:00Z");

    private static final Instant SPAETER = MITTAGS.plusSeconds(3600L);

    /**
     * Zwei Rollen, von denen die eine mehr darf als die andere.
     *
     * <p>Eigene Rollen und nicht {@link Teamwerte#VORGABE}: Was die Vorgabe vergibt, ist
     * eine Einstellung und darf sich aendern. Eine Probe, die daran haengt, bricht bei
     * jeder Aenderung an der Vorgabe ein - und dann wird sie angepasst, statt gelesen.
     */
    private static final Teamwerte WERTE = new Teamwerte(List.of(
            new Teamwerte.Rolle("helfer", List.of("tpa")),
            new Teamwerte.Rolle("verwalter", List.of("tpa", "besitz", "verwalten"))));

    /** Die Rechte eines Verwalters - er darf beide Rollen vergeben. */
    private static final Set<String> VERWALTERRECHTE = Set.of("freimarken.tpa",
            "freimarken.besitz", "freimarken.verwalten");

    /** Die Rechte eines Helfers - ihm fehlen zwei der drei Verwalterrechte. */
    private static final Set<String> HELFERRECHTE = Set.of("freimarken.tpa");

    private static Teambuch buch(Teamablage ablage) {
        return new Teambuch(ablage, () -> WERTE);
    }

    @Test
    @DisplayName("Ein Verwalter vergibt die Verwalterrolle")
    void verwalterVergibtVerwalter() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        UUID chef = UUID.randomUUID();

        Teambuch.Entscheid entscheid =
                buch(ablage).gib(anna, "verwalter", MITTAGS, chef, VERWALTERRECHTE);

        assertEquals(Teambuch.Bescheid.VERGEBEN, entscheid.bescheid());
        assertTrue(entscheid.geaendert(), "das gehoert ins Protokoll");
        assertEquals("verwalter", entscheid.rolle().orElseThrow().schluessel());
        assertEquals(List.of("verwalter"), ablage.lies(anna).rollen(),
                "und es steht wirklich in der Ablage");
    }

    @Test
    @DisplayName("DER RIEGEL: Ein Helfer vergibt die Verwalterrolle NICHT")
    void helferVergibtKeinenVerwalter() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        UUID helfer = UUID.randomUUID();

        // DER ZUSTAND, IN DEM DER RIEGEL GREIFT: Ein Helfer mit team.vergeben braeuchte
        // sonst nur einen Komplizen zum Verwalter zu machen und haette ueber ihn
        // freimarken.besitz - also die Vollmacht, fremden Besitz umzuschreiben, die er
        // selbst nicht hat.
        Teambuch.Entscheid entscheid =
                buch(ablage).gib(anna, "verwalter", MITTAGS, helfer, HELFERRECHTE);

        assertEquals(Teambuch.Bescheid.ZU_WENIG_RECHTE, entscheid.bescheid());
        assertFalse(entscheid.geaendert());
        // DIE FEHLENDEN VOLLSTAENDIG und nicht nur das erste: Wer eine Rolle einrichtet,
        // soll in einer Antwort lesen, was ihm fehlt.
        assertEquals(List.of("freimarken.besitz", "freimarken.verwalten"),
                entscheid.fehlende(), "beide, nicht nur eines");
        assertFalse(ablage.lies(anna).imTeam(), "und in der Ablage steht NICHTS");
    }

    @Test
    @DisplayName("Ein Helfer vergibt die Helferrolle - seine eigene Vollmacht")
    void helferVergibtHelfer() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        UUID helfer = UUID.randomUUID();

        // Das Gegenstueck zur Probe darueber: Derselbe Vergebende, dieselbe Rechtemenge,
        // NUR die Rolle ist eine andere. Ohne diese Probe koennte der Riegel "ein Helfer
        // darf gar nichts vergeben" lauten, und das waere eine andere Regel.
        Teambuch.Entscheid entscheid =
                buch(ablage).gib(anna, "helfer", MITTAGS, helfer, HELFERRECHTE);

        assertEquals(Teambuch.Bescheid.VERGEBEN, entscheid.bescheid());
        assertEquals(List.of("helfer"), ablage.lies(anna).rollen());
    }

    @Test
    @DisplayName("DER RIEGEL AUCH BEIM NEHMEN: Ein Helfer entwaffnet keinen Verwalter")
    void helferNimmtKeinenVerwalter() {
        Teamablage ablage = new SpeicherTeam();
        UUID chef = UUID.randomUUID();
        UUID helfer = UUID.randomUUID();
        assertTrue(ablage.gib(chef, "verwalter", MITTAGS, null), "die Vorbereitung");

        Teambuch.Entscheid entscheid = buch(ablage).nimm(chef, "verwalter", HELFERRECHTE);

        // Er gewinnt dabei nichts - aber er entwaffnet den, der ihn aufhalten koennte.
        // Ein Server, auf dem die untere Rolle die obere abbauen kann, hat keine
        // Rangordnung, sondern eine Reihenfolge.
        assertEquals(Teambuch.Bescheid.ZU_WENIG_RECHTE, entscheid.bescheid());
        assertEquals(List.of("verwalter"), ablage.lies(chef).rollen(),
                "der Verwalter ist noch Verwalter");
    }

    @Test
    @DisplayName("Ein Verwalter nimmt die Verwalterrolle")
    void verwalterNimmtVerwalter() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        UUID chef = UUID.randomUUID();
        assertTrue(ablage.gib(anna, "verwalter", MITTAGS, null), "die Vorbereitung");

        Teambuch.Entscheid entscheid = buch(ablage).nimm(anna, "verwalter",
                VERWALTERRECHTE);

        assertEquals(Teambuch.Bescheid.GENOMMEN, entscheid.bescheid());
        assertTrue(entscheid.geaendert());
        assertFalse(ablage.lies(anna).imTeam());
    }

    @Test
    @DisplayName("Sich selbst eine Rolle geben ist erlaubt - der Riegel genuegt")
    void selbstvergabeIstErlaubt() {
        Teamablage ablage = new SpeicherTeam();
        UUID chef = UUID.randomUUID();

        // KEIN zweiter Riegel: Der Rechteriegel sorgt ohnehin dafuer, dass dabei kein
        // Recht entsteht, das der Vergebende nicht schon hat. Ein zusaetzliches
        // Selbstvergabeverbot waere ein zweiter Riegel fuer denselben Fall - nimmt man
        // den einen heraus, bliebe die Probe gruen, und dann ist keiner von beiden
        // geprueft.
        Teambuch.Entscheid entscheid =
                buch(ablage).gib(chef, "verwalter", MITTAGS, chef, VERWALTERRECHTE);

        assertEquals(Teambuch.Bescheid.VERGEBEN, entscheid.bescheid());
        assertEquals(chef, ablage.lies(chef).zeilen().get(0).vergeber().orElseThrow(),
                "und es steht da, dass er es selbst war");
    }

    @Test
    @DisplayName("Sich selbst hochsetzen geht NICHT - derselbe Riegel")
    void selbstHochsetzenGehtNicht() {
        Teamablage ablage = new SpeicherTeam();
        UUID helfer = UUID.randomUUID();

        // Das ist der Fall, gegen den ein Selbstvergabeverbot gebaut waere - und der
        // Rechteriegel faengt ihn schon. Darum braucht es das Verbot nicht.
        Teambuch.Entscheid entscheid =
                buch(ablage).gib(helfer, "verwalter", MITTAGS, helfer, HELFERRECHTE);

        assertEquals(Teambuch.Bescheid.ZU_WENIG_RECHTE, entscheid.bescheid());
        assertFalse(ablage.lies(helfer).imTeam());
    }

    @Test
    @DisplayName("Die Konsole darf alles - sie hat ohnehin die Datei")
    void konsoleDarfAlles() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();

        // null als Rechtemenge heisst DIE KONSOLE. Wer an der Konsole steht, hat
        // Dateizugriff; ein Riegel, den man umgeht, indem man eine Zeile tippt, ist kein
        // Riegel, sondern eine Unbequemlichkeit, die ein Betreiber umgeht.
        Teambuch.Entscheid entscheid =
                buch(ablage).gib(anna, "verwalter", MITTAGS, null, null);

        assertEquals(Teambuch.Bescheid.VERGEBEN, entscheid.bescheid());
        assertTrue(ablage.lies(anna).zeilen().get(0).vergeber().isEmpty(),
                "und ohne Vergeber, denn die Konsole hat keine UUID");
    }

    @Test
    @DisplayName("Eine LEERE Rechtemenge ist nicht die Konsole - ihr fehlt alles")
    void leereRechtemengeIstNichtDieKonsole() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        UUID niemand = UUID.randomUUID();

        // Der Unterschied zwischen null und Set.of() ist hier der ganze Riegel: Ein
        // Spieler ohne jedes Recht darf nichts vergeben, die Konsole alles. Wer die
        // beiden Faelle in einem behandelte, machte aus jedem rechtelosen Spieler einen
        // Betreiber.
        assertEquals(Teambuch.Bescheid.ZU_WENIG_RECHTE,
                buch(ablage).gib(anna, "helfer", MITTAGS, niemand, Set.of()).bescheid());
        assertFalse(ablage.lies(anna).imTeam());
    }

    @Test
    @DisplayName("Eine Rolle, die dieser Server nicht kennt, wird nicht vergeben")
    void unbekannteRolleWirdNichtVergeben() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();

        Teambuch.Entscheid entscheid =
                buch(ablage).gib(anna, "hausmeister", MITTAGS, null, null);

        assertEquals(Teambuch.Bescheid.UNBEKANNTE_ROLLE, entscheid.bescheid());
        assertTrue(entscheid.rolle().isEmpty());
        assertFalse(ablage.lies(anna).imTeam(),
                "sonst stuende sie fuer immer in der Liste und gaebe keine Rechte");
    }

    @Test
    @DisplayName("Eine weggefallene Rolle laesst sich trotzdem NEHMEN")
    void weggefalleneRolleLaesstSichNehmen() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        // Der Zustand: Der Betreiber hat die Zeile "hausmeister" aus der config.yml
        // genommen, waehrend anna die Rolle hatte.
        assertTrue(ablage.gib(anna, "hausmeister", MITTAGS, null), "die Vorbereitung");

        Teambuch.Entscheid entscheid = buch(ablage).nimm(anna, "hausmeister",
                VERWALTERRECHTE);

        // Sonst bliebe sie fuer immer in der Ablage stehen, und die Teamliste zeigte
        // einen Eintrag, den niemand loeschen kann.
        assertEquals(Teambuch.Bescheid.GENOMMEN, entscheid.bescheid());
        assertTrue(entscheid.rolle().isEmpty(), "die Datei kennt sie nicht mehr");
        assertFalse(ablage.lies(anna).imTeam());
    }

    @Test
    @DisplayName("Ein zweites Geben meldet HATTE_SCHON und aendert nichts")
    void zweitesGebenMeldetHatteSchon() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();
        UUID chef = UUID.randomUUID();
        assertEquals(Teambuch.Bescheid.VERGEBEN, buch(ablage)
                .gib(anna, "helfer", MITTAGS, chef, VERWALTERRECHTE).bescheid());

        Teambuch.Entscheid entscheid =
                buch(ablage).gib(anna, "helfer", SPAETER, chef, VERWALTERRECHTE);

        assertEquals(Teambuch.Bescheid.HATTE_SCHON, entscheid.bescheid());
        assertFalse(entscheid.geaendert(), "das gehoert NICHT ins Protokoll");
        assertEquals(MITTAGS, ablage.lies(anna).zeilen().get(0).seit(),
                "und die erste Vergabezeit steht noch da");
    }

    @Test
    @DisplayName("Eine Rolle nehmen, die er nicht hat, meldet HATTE_NICHT")
    void nehmenOhneRolleMeldetHatteNicht() {
        Teamablage ablage = new SpeicherTeam();
        UUID anna = UUID.randomUUID();

        Teambuch.Entscheid entscheid = buch(ablage).nimm(anna, "helfer",
                VERWALTERRECHTE);

        assertEquals(Teambuch.Bescheid.HATTE_NICHT, entscheid.bescheid());
        assertFalse(entscheid.geaendert());
    }

    @Test
    @DisplayName("Ohne Rollen in der Datei wird nichts vergeben")
    void ohneRollenWirdNichtsVergeben() {
        Teamablage ablage = new SpeicherTeam();
        Teambuch leeres = new Teambuch(ablage, () -> new Teamwerte(List.of()));
        UUID anna = UUID.randomUUID();

        // Ein eigener Bescheid und nicht UNBEKANNTE_ROLLE: "diesen Server hat kein Team"
        // und "diese Rolle gibt es nicht" sind zwei verschiedene Auskuenfte, und der
        // Betreiber sucht an zwei verschiedenen Stellen.
        assertEquals(Teambuch.Bescheid.KEIN_TEAM,
                leeres.gib(anna, "helfer", MITTAGS, null, null).bescheid());
    }

    @Test
    @DisplayName("Eine werfende Ablage wird zum Bescheid und nicht zum Wurf")
    void werfendeAblageWirdZumBescheid() {
        Teambuch gestoert = new Teambuch(new WerfendeAblage(), () -> WERTE);
        UUID anna = UUID.randomUUID();

        // Der Aufrufer ist ein Befehl, und ein Wurf dort ist eine rote englische Zeile
        // fuer eine Lage, die der Betreiber versteht, sobald sie auf Deutsch dasteht.
        assertEquals(Teambuch.Bescheid.GESTOERT,
                gestoert.gib(anna, "helfer", MITTAGS, null, null).bescheid());
        assertEquals(Teambuch.Bescheid.GESTOERT,
                gestoert.nimm(anna, "helfer", null).bescheid());
    }

    @Test
    @DisplayName("Fehlende Werte fallen auf die Vorgabe zurueck")
    void fehlendeWerteFallenZurueck() {
        // Der Lieferant kann null geben, wenn das Neuladen mitten im Zug steht. Ein
        // Wurf waere ein Befehl, der in genau dieser Sekunde abbricht.
        Teambuch buch = new Teambuch(new SpeicherTeam(), () -> null);

        assertEquals(Teamwerte.VORGABE.schluessel(), buch.werte().schluessel());
    }

    @Test
    @DisplayName("Ohne Ablage oder Werte gibt es kein Teambuch")
    void ohneAblageKeinBuch() {
        assertThrows(IllegalArgumentException.class,
                () -> new Teambuch(null, () -> WERTE));
        assertThrows(IllegalArgumentException.class,
                () -> new Teambuch(new SpeicherTeam(), null));
    }

    @Test
    @DisplayName("ZU_WENIG_RECHTE ohne fehlendes Recht wird abgewiesen")
    void zuWenigRechteBrauchtEinenGrund() {
        // Sonst saehe die Absage aus wie eine Absage ohne Grund, und der Betreiber
        // suchte an der falschen Stelle.
        assertThrows(IllegalArgumentException.class,
                () -> new Teambuch.Entscheid(Teambuch.Bescheid.ZU_WENIG_RECHTE,
                        java.util.Optional.empty(), List.of()));
    }

    /** Eine Ablage, die bei jedem Schreibzug wirft - fuer den Bescheid GESTOERT. */
    private static final class WerfendeAblage implements Teamablage {
        @Override
        public Teamstand lies(UUID spieler) {
            return Teamstand.leer(spieler);
        }

        @Override
        public boolean gib(UUID spieler, String rolle, Instant wann, UUID vonWem) {
            throw new IllegalStateException("Teamrolle nicht schreibbar");
        }

        @Override
        public boolean nimm(UUID spieler, String rolle) {
            throw new IllegalStateException("Teamrolle nicht loeschbar");
        }

        @Override
        public List<Teamstand> alle() {
            throw new IllegalStateException("Teamliste nicht lesbar");
        }
    }
}
