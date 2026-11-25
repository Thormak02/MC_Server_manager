package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Riegel der Strafen - wer darf, wie lange, und gegen wen.
 *
 * <p><b>Die tragenden Proben sind {@link #zuLangeBrauchtDasRecht()} und
 * {@link #genauAnDerGrenzeGehtDurch()}.</b> Sie halten zusammen den Riegel aus Plan
 * 12c.2 fest: Der Moderator bannt zeitlich, der Verwalter dauerhaft. Die erste faengt
 * den Weg um den Riegel herum - eine formal befristete Dauer von zehn Jahren. Die zweite
 * haelt die Grenze SELBST fest: Ohne sie waeren {@code >} und {@code >=} dasselbe, und
 * niemand saehe, dass die erlaubte Hoechstdauer selbst verboten ist.
 *
 * <p>Die Rechte kommen als Menge herein. <b>{@code null} heisst die KONSOLE und darf
 * alles; {@code Set.of()} ist ein Spieler ohne jedes Recht</b> - der Unterschied IST der
 * Riegel, und beide Faelle stehen in {@link #konsoleDarfAlles()} und
 * {@link #ohneRechteGehtNichts()}.
 */
class StrafbuchTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant SPAETER = MITTAGS.plusSeconds(60L);

    private static final UUID BERT = UUID.randomUUID();
    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID CLARA = UUID.randomUUID();

    private static final Strafdauer EINE_STUNDE =
            new Strafdauer(Duration.ofHours(1L));

    /** Die Namen wie im Plugin - der Riegel bleibt hier, die Namen kommen von dort. */
    private static final Strafrechte NAMEN = new Strafrechte(
            "freimarken.strafen.stumm", "freimarken.strafen.rauswurf",
            "freimarken.strafen.bann", "freimarken.strafen.dauerhaft",
            "freimarken.strafen.aufheben", "freimarken.strafen.team");

    /** Ein Moderator: darf alle drei Arten und aufheben, aber nichts Dauerhaftes. */
    private static final Set<String> MODERATOR = Set.of(NAMEN.stumm(),
            NAMEN.rauswurf(), NAMEN.bann(), NAMEN.aufheben());

    /** Ein Verwalter: dazu das Dauerhafte und das Team. */
    private static final Set<String> VERWALTER = Set.of(NAMEN.stumm(),
            NAMEN.rauswurf(), NAMEN.bann(), NAMEN.aufheben(), NAMEN.dauerhaft(),
            NAMEN.team());

    /**
     * Wen die Teamfrage als Teammitglied meldet - je Probe gesetzt.
     *
     * <p>Die Frage steht seit dem 25.11.2026 im BUCH und nicht als Parameter an
     * {@code verhaenge}: {@code hebeAuf} braucht sie genauso, kennt den Betroffenen
     * aber erst nach dem Lesen. Genau darum fehlte der Riegel dort ganz.
     */
    private final java.util.Set<UUID> imTeam = new java.util.HashSet<>();

    @org.junit.jupiter.api.BeforeEach
    void frisch() {
        imTeam.clear();
    }

    private Strafbuch buch() {
        return buch(Strafwerte.VORGABE);
    }

    private Strafbuch buch(Strafwerte werte) {
        return new Strafbuch(new SpeicherStrafen(), () -> werte, NAMEN,
                imTeam::contains);
    }

    /** Eine Ablage und ein Buch, damit eine Probe die Strafe vorher legen kann. */
    private Strafbuch buchMit(SpeicherStrafen ablage, Strafwerte werte) {
        return new Strafbuch(ablage, () -> werte, NAMEN, imTeam::contains);
    }

    // ------------------------------------------------------------------ verhaengen

    @Test
    @DisplayName("Ein Moderator verhaengt einen befristeten Bann")
    void moderatorBanntBefristet() {
        Strafbuch buch = buch();

        Strafbuch.Entscheid entscheid = buch.verhaenge(BERT, Strafart.BANN,
                "Grief in Altfurt", EINE_STUNDE, ANNA, MODERATOR, MITTAGS);

        assertEquals(Strafbuch.Bescheid.VERHAENGT, entscheid.bescheid());
        assertTrue(entscheid.geaendert());
        Strafe eine = entscheid.strafe().orElseThrow();
        assertEquals(BERT, eine.betroffener());
        assertEquals("Grief in Altfurt", eine.grund());
        assertEquals(Optional.of(ANNA), eine.verhaengtVon());
        assertTrue(eine.giltNoch(SPAETER));
    }

    @Test
    @DisplayName("Ohne das Recht fuer die ART geht nichts - je Art geprueft")
    void ohneArtrechtGehtNichts() {
        Strafbuch buch = buch();

        // Je Art eigens: Ein Riegel, der nur eine Art prueft, laesst die anderen
        // offen - und das faellt nie auf, weil die eine gepruefte gruen ist.
        for (Strafart art : Strafart.values()) {
            Strafdauer dauer = art.brauchtDauer() ? EINE_STUNDE : null;
            assertEquals(Strafbuch.Bescheid.OHNE_RECHT,
                    buch.verhaenge(BERT, art, "Grund", dauer, ANNA,
                            Set.of("freimarken.heim"), MITTAGS).bescheid(),
                    art + ": ohne das Artrecht geht es nicht");
            assertEquals(Strafbuch.Bescheid.VERHAENGT,
                    buch.verhaenge(UUID.randomUUID(), art, "Grund", dauer, ANNA,
                            Set.of(NAMEN.fuer(art)), MITTAGS).bescheid(),
                    art + ": mit dem Artrecht geht es");
        }
    }

    @Test
    @DisplayName("Wer nur kicken darf, bannt nicht")
    void kickrechtIstKeinBannrecht() {
        Strafbuch buch = buch();
        Set<String> nurKick = Set.of(NAMEN.rauswurf());

        assertEquals(Strafbuch.Bescheid.VERHAENGT, buch.verhaenge(BERT,
                Strafart.RAUSWURF, "Spam", null, ANNA, nurKick, MITTAGS).bescheid());
        assertEquals(Strafbuch.Bescheid.OHNE_RECHT, buch.verhaenge(BERT,
                Strafart.BANN, "Spam", EINE_STUNDE, ANNA, nurKick, MITTAGS).bescheid(),
                "die Trennung aus Plan 12c.2 ist nur eine, wenn sie greift");
    }

    @Test
    @DisplayName("Eine UNBEGRENZTE Strafe braucht das Recht fuer Dauerhaftes")
    void unbegrenztBrauchtDasRecht() {
        Strafbuch buch = buch();

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.verhaenge(BERT, Strafart.BANN, "fuer immer",
                        Strafdauer.UNBEGRENZT, ANNA, MODERATOR, MITTAGS).bescheid());
        assertEquals(Strafbuch.Bescheid.VERHAENGT,
                buch.verhaenge(BERT, Strafart.BANN, "fuer immer",
                        Strafdauer.UNBEGRENZT, ANNA, VERWALTER, MITTAGS).bescheid(),
                "der Verwalter darf es - sonst darf es niemand, und der Riegel waere"
                        + " ein Verbot");
    }

    @Test
    @DisplayName("Eine ZU LANGE Dauer braucht dasselbe Recht - der Weg um den Riegel")
    void zuLangeBrauchtDasRecht() {
        // DIE PROBE DIESES TEILS. Ohne diesen zweiten Faenger tippt ein Moderator
        // 3650d: formal befristet, praktisch endgueltig. Der Riegel waere dann einer,
        // den man umgeht, ohne ihn zu beruehren.
        Strafbuch buch = buch(Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN,
                720L));
        Strafdauer zehnJahre = new Strafdauer(Duration.ofDays(3650L));

        Strafbuch.Entscheid entscheid = buch.verhaenge(BERT, Strafart.BANN,
                "zehn Jahre", zehnJahre, ANNA, MODERATOR, MITTAGS);

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT, entscheid.bescheid());
        assertEquals(720L, entscheid.grenzeStunden(),
                "die Absage NENNT die Grenze - wer nur \"zu lang\" liest, tippt als"
                        + " naechstes dieselbe Zahl");
        assertEquals(Strafbuch.Bescheid.VERHAENGT, buch.verhaenge(BERT,
                Strafart.BANN, "zehn Jahre", zehnJahre, ANNA, VERWALTER, MITTAGS)
                .bescheid());
    }

    @Test
    @DisplayName("GENAU an der Grenze geht es durch, eine Stunde darueber nicht")
    void genauAnDerGrenzeGehtDurch() {
        // Der DRITTE Zustand mit eigenem Satz: Ohne diese Probe waeren > und >=
        // dasselbe, solange keine Zahl die Gleichheit erreicht - und dann waere die
        // erlaubte Hoechstdauer selbst verboten, ohne dass es jemand merkt.
        Strafbuch buch = buch(Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN,
                720L));

        assertEquals(Strafbuch.Bescheid.VERHAENGT,
                buch.verhaenge(BERT, Strafart.BANN, "genau dreissig Tage",
                        new Strafdauer(Duration.ofHours(720L)), ANNA, MODERATOR,
                        MITTAGS).bescheid(),
                "720 Stunden sind erlaubt, wenn 720 die Grenze ist");
        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.verhaenge(CLARA, Strafart.BANN, "eine Stunde zu lang",
                        new Strafdauer(Duration.ofHours(721L)), ANNA, MODERATOR,
                        MITTAGS).bescheid(),
                "721 Stunden nicht");
    }

    @Test
    @DisplayName("Die Grenze gilt je ART getrennt")
    void grenzeGiltJeArt() {
        // Sieben Tage stumm ist eine harte Strafe, sieben Tage gebannt eine
        // gewoehnliche. Eine gemeinsame Zahl waere fuer die eine zu hoch und fuer die
        // andere zu niedrig.
        Strafbuch buch = buch(Strafwerte.VORGABE
                .mitHoechsteStunden(Strafart.STUMM, 24L)
                .mitHoechsteStunden(Strafart.BANN, 720L));
        Strafdauer zweiTage = new Strafdauer(Duration.ofDays(2L));

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.verhaenge(BERT, Strafart.STUMM, "zwei Tage stumm",
                        zweiTage, ANNA, MODERATOR, MITTAGS).bescheid());
        assertEquals(Strafbuch.Bescheid.VERHAENGT,
                buch.verhaenge(BERT, Strafart.BANN, "zwei Tage gebannt",
                        zweiTage, ANNA, MODERATOR, MITTAGS).bescheid());
    }

    @Test
    @DisplayName("Eine Hoechstdauer von 0 verbietet jede Dauer ohne das Recht")
    void nullStundenVerbietetAlles() {
        Strafbuch buch = buch(Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN, 0L));

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.verhaenge(BERT, Strafart.BANN, "eine Stunde", EINE_STUNDE,
                        ANNA, MODERATOR, MITTAGS).bescheid(),
                "eine 0 heisst: jeder Bann braucht das Recht fuer dauerhafte Strafen");
    }

    @Test
    @DisplayName("Ein RAUSWURF kennt die Grenze nicht - er hat keine Dauer")
    void rauswurfOhneGrenze() {
        Strafbuch buch = buch(Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN, 0L));

        // Ein Riegel, der auch den Rauswurf traefe, waere einer, den die Hoechstdauer
        // einer anderen Art zuschlaegt.
        assertEquals(Strafbuch.Bescheid.VERHAENGT, buch.verhaenge(BERT,
                Strafart.RAUSWURF, "Spam", null, ANNA, MODERATOR, MITTAGS).bescheid());
    }

    @Test
    @DisplayName("DIE ABSCHNEIDUNG: 720 Std. und 59 Min. gehen NICHT durch")
    void abschneidungGehtNichtDurch() {
        // DER FUND VOM 25.11.2026. Der Riegel fragte "stunden() > grenze", und
        // Duration.toHours() SCHNEIDET AB: 43259 Minuten sind 720 Std. 59 Min., und
        // toHours() gab 720 - "720 > 720" ist falsch, die Strafe kam durch und lief
        // 59 Minuten laenger als die Hoechstdauer der Datei.
        Strafbuch buch = buch(Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN,
                720L));

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.verhaenge(BERT, Strafart.BANN, "neunundfuenfzig zu viel",
                        new Strafdauer(Duration.ofMinutes(43259L)), ANNA, MODERATOR,
                        MITTAGS).bescheid(),
                "720 Std. 59 Min. liegen ueber 720 Std.");
        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.verhaenge(BERT, Strafart.BANN, "eine Sekunde zu viel",
                        new Strafdauer(Duration.ofSeconds(2592001L)), ANNA, MODERATOR,
                        MITTAGS).bescheid(),
                "auch eine Sekunde darueber");
        assertEquals(Strafbuch.Bescheid.VERHAENGT,
                buch.verhaenge(BERT, Strafart.BANN, "genau 720 Std.",
                        new Strafdauer(Duration.ofSeconds(2592000L)), ANNA, MODERATOR,
                        MITTAGS).bescheid(),
                "und genau 720 Std. gehen weiter durch");
    }

    @Test
    @DisplayName("DIE 0 HAELT: sie sperrt auch Dauern unter einer Stunde")
    void nullStundenSperrtAuchMinuten() {
        // DER SCHWERE TEIL DESSELBEN FUNDES: Mit der Grenze 0 gab stunden() fuer
        // jede Dauer unter einer Stunde eine 0, und "0 > 0" ist falsch. Die
        // config.yml, der Startbericht UND die Absage sagen aber ausdruecklich zu,
        // dass 0 heisst: Diese Art kann ein Moderator ohne das Recht GAR NICHT
        // verhaengen.
        Strafbuch buch = buch(Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN, 0L));

        for (long minuten : new long[] {1L, 30L, 59L}) {
            assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                    buch.verhaenge(BERT, Strafart.BANN, "Grief",
                            new Strafdauer(Duration.ofMinutes(minuten)), ANNA,
                            MODERATOR, MITTAGS).bescheid(),
                    minuten + " Minuten - eine 0 sperrt JEDE Dauer");
        }
        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.verhaenge(BERT, Strafart.BANN, "Grief",
                        new Strafdauer(Duration.ofSeconds(1L)), ANNA, MODERATOR,
                        MITTAGS).bescheid(),
                "sogar eine Sekunde");
    }

    @Test
    @DisplayName("Wer im TEAM ist, wird nur von jemandem mit dem Teamrecht gestraft")
    void teamBrauchtDasTeamrecht() {
        // Ohne diesen Riegel bannen zwei Moderatoren im Streit einander, und wer
        // schneller tippt, hat gewonnen. Das ist die Entsprechung zu dem Riegel aus
        // Teil A - dieselbe Gefahr aus der anderen Richtung.
        Strafbuch buch = buch();

        imTeam.add(BERT);

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_TEAM,
                buch.verhaenge(BERT, Strafart.BANN, "Kollege im Streit",
                        EINE_STUNDE, ANNA, MODERATOR, MITTAGS).bescheid());
        assertEquals(Strafbuch.Bescheid.VERHAENGT,
                buch.verhaenge(BERT, Strafart.BANN, "Kollege im Streit",
                        EINE_STUNDE, ANNA, VERWALTER, MITTAGS).bescheid(),
                "ein Verwalter darf es - sonst waere ein Teammitglied unantastbar");
    }

    @Test
    @DisplayName("Der Teamriegel greift bei JEDER Art, auch beim Rauswurf")
    void teamriegelBeiJederArt() {
        Strafbuch buch = buch();

        // Ein Riegel, der nur den Bann schuetzt, laesst den Rauswurf offen - und ein
        // Moderator, der seinen Kollegen alle zehn Sekunden hinauswirft, braucht
        // keinen Bann.
        imTeam.add(BERT);
        for (Strafart art : Strafart.values()) {
            assertEquals(Strafbuch.Bescheid.OHNE_RECHT_TEAM,
                    buch.verhaenge(BERT, art, "Kollege",
                            art.brauchtDauer() ? EINE_STUNDE : null, ANNA, MODERATOR,
                            MITTAGS).bescheid(),
                    art + ": auch diese Art braucht das Teamrecht");
        }
    }

    @Test
    @DisplayName("Das Artrecht wird VOR dem Teamrecht geprueft")
    void artrechtVorTeamrecht() {
        Strafbuch buch = buch();

        // Wer gar nicht bannen darf, soll nicht lesen, dass ihm das Recht gegen
        // Teammitglieder fehlt - das klaenge, als fehlte ihm nur eines.
        imTeam.add(BERT);

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT,
                buch.verhaenge(BERT, Strafart.BANN, "Kollege", EINE_STUNDE, ANNA,
                        Set.of("freimarken.heim"), MITTAGS).bescheid());
    }

    @Test
    @DisplayName("Niemand straft sich selbst")
    void niemandStraftSichSelbst() {
        Strafbuch buch = buch();

        assertEquals(Strafbuch.Bescheid.SELBST, buch.verhaenge(ANNA,
                Strafart.BANN, "aus Versehen", EINE_STUNDE, ANNA, VERWALTER, MITTAGS)
                .bescheid());
    }

    @Test
    @DisplayName("Die Selbstbestrafung wird OHNE Datenbank erkannt")
    void selbstOhneDatenbank() {
        // Die Reihenfolge der Fragen ist die Zusage: Wer sich selbst bestrafen will,
        // soll das ohne Lesezugriff erfahren. Mit einer werfenden Ablage ist das
        // messbar - kaeme der Zugriff vorher, stuende hier GESTOERT.
        Strafbuch buch = new Strafbuch(new WerfendeAblage(), () -> Strafwerte.VORGABE,
                NAMEN, imTeam::contains);

        assertEquals(Strafbuch.Bescheid.SELBST, buch.verhaenge(ANNA,
                Strafart.BANN, "aus Versehen", EINE_STUNDE, ANNA, VERWALTER, MITTAGS)
                .bescheid());
    }

    @Test
    @DisplayName("Ein unbrauchbarer Grund wird abgewiesen - auch von der Konsole")
    void grundIstPflicht() {
        Strafbuch buch = buch();

        // Plan 12c.3 traegt darauf, und zwar ohne Ausnahme: Auch die Konsole erklaert,
        // was sie tut. Ein Schalter dafuer waere die Einstellung, mit der ein
        // Betreiber seine eigene Moderation in Willkuer verwandelt.
        assertEquals(Strafbuch.Bescheid.GRUND_UNBRAUCHBAR, buch.verhaenge(BERT,
                Strafart.BANN, "ab", EINE_STUNDE, ANNA, VERWALTER, MITTAGS).bescheid());
        assertEquals(Strafbuch.Bescheid.GRUND_UNBRAUCHBAR, buch.verhaenge(BERT,
                Strafart.BANN, "", EINE_STUNDE, ANNA, null, MITTAGS).bescheid());
        assertEquals(Strafbuch.Bescheid.GRUND_UNBRAUCHBAR, buch.verhaenge(BERT,
                Strafart.BANN, null, EINE_STUNDE, ANNA, VERWALTER, MITTAGS).bescheid());
        assertEquals(Strafbuch.Bescheid.GRUND_UNBRAUCHBAR, buch.verhaenge(BERT,
                Strafart.BANN, "Grief §cFAKE", EINE_STUNDE, ANNA, VERWALTER,
                MITTAGS).bescheid());
    }

    @Test
    @DisplayName("Eine zweite Strafe derselben Art wird abgewiesen und NENNT die erste")
    void zweiteStrafeAbgewiesen() {
        Strafbuch buch = buch();
        buch.verhaenge(BERT, Strafart.BANN, "erster Bann", EINE_STUNDE, ANNA,
                MODERATOR, MITTAGS);

        Strafbuch.Entscheid zweiter = buch.verhaenge(BERT, Strafart.BANN,
                "zweiter Bann", EINE_STUNDE, CLARA, MODERATOR, SPAETER);

        // Zwei geltende Banne machen die Frage "wann ist er wieder frei"
        // unbeantwortbar. Und die Absage muss die NUMMER nennen - sonst ist der Weg
        // aus ihr heraus verschlossen.
        assertEquals(Strafbuch.Bescheid.SCHON_GESTRAFT, zweiter.bescheid());
        assertEquals("erster Bann", zweiter.strafe().orElseThrow().grund());
        assertFalse(zweiter.geaendert());
    }

    @Test
    @DisplayName("Eine zweite Strafe einer ANDEREN Art geht durch")
    void andereArtGehtDurch() {
        Strafbuch buch = buch();
        buch.verhaenge(BERT, Strafart.STUMM, "stumm", EINE_STUNDE, ANNA,
                MODERATOR, MITTAGS);

        // Stumm und gebannt sind zwei Sachen. Ein Riegel, der die Arten nicht
        // trennte, verhinderte den Bann eines Stummgeschalteten - also genau den Fall,
        // in dem es ernst wird.
        assertEquals(Strafbuch.Bescheid.VERHAENGT, buch.verhaenge(BERT,
                Strafart.BANN, "dann eben Bann", EINE_STUNDE, ANNA, MODERATOR, SPAETER)
                .bescheid());
    }

    @Test
    @DisplayName("Ein zweiter RAUSWURF geht immer durch")
    void zweiterRauswurfGehtDurch() {
        Strafbuch buch = buch();
        buch.verhaenge(BERT, Strafart.RAUSWURF, "erster", null, ANNA, MODERATOR,
                MITTAGS);

        // Ein Rauswurf gilt nie noch, also steht ihm nie einer entgegen. Ohne diese
        // Zusage liesse sich ein Spieler genau einmal hinauswerfen.
        assertEquals(Strafbuch.Bescheid.VERHAENGT, buch.verhaenge(BERT,
                Strafart.RAUSWURF, "zweiter", null, ANNA, MODERATOR, SPAETER)
                .bescheid());
        assertEquals(2, buch.zaehle(BERT, Strafart.RAUSWURF));
    }

    @Test
    @DisplayName("Nach ABLAUF der ersten geht die zweite durch")
    void nachAblaufGehtDieZweite() {
        Strafbuch buch = buch();
        buch.verhaenge(BERT, Strafart.BANN, "erster", EINE_STUNDE, ANNA,
                MODERATOR, MITTAGS);

        assertEquals(Strafbuch.Bescheid.VERHAENGT, buch.verhaenge(BERT,
                Strafart.BANN, "zweiter", EINE_STUNDE, ANNA, MODERATOR,
                MITTAGS.plusSeconds(3601L)).bescheid());
    }

    @Test
    @DisplayName("Die KONSOLE darf alles - auch dauerhaft und gegen das Team")
    void konsoleDarfAlles() {
        Strafbuch buch = buch();

        // null heisst die Konsole. Wer an ihr steht, hat Dateizugriff - ein Riegel
        // dort waere einer, den man mit einem Texteditor umgeht.
        imTeam.add(BERT);

        assertEquals(Strafbuch.Bescheid.VERHAENGT, buch.verhaenge(BERT,
                Strafart.BANN, "von der Konsole", Strafdauer.UNBEGRENZT, null, null,
                MITTAGS).bescheid());
    }

    @Test
    @DisplayName("Eine LEERE Rechtemenge darf nichts - sie ist nicht die Konsole")
    void ohneRechteGehtNichts() {
        Strafbuch buch = buch();

        for (Strafart art : Strafart.values()) {
            assertEquals(Strafbuch.Bescheid.OHNE_RECHT,
                    buch.verhaenge(BERT, art, "Grund",
                            art.brauchtDauer() ? EINE_STUNDE : null, ANNA, Set.of(),
                            MITTAGS).bescheid(),
                    art + ": eine leere Menge ist ein Spieler ohne jedes Recht");
        }
    }

    @Test
    @DisplayName("Eine werfende Ablage gibt GESTOERT und keinen Wurf")
    void werfendeAblageGibtGestoert() {
        Strafbuch buch = new Strafbuch(new WerfendeAblage(), () -> Strafwerte.VORGABE,
                NAMEN, imTeam::contains);

        // Ein Wurf im Befehl ist eine rote englische Zeile fuer eine Lage, die der
        // Moderator versteht, sobald sie auf Deutsch dasteht.
        assertEquals(Strafbuch.Bescheid.GESTOERT, buch.verhaenge(BERT,
                Strafart.BANN, "Grief", EINE_STUNDE, ANNA, VERWALTER, MITTAGS)
                .bescheid());
        assertEquals(Strafbuch.Bescheid.GESTOERT, buch.hebeAuf(1L, ANNA, "Einspruch",
                MITTAGS, VERWALTER).bescheid());
    }

    @Test
    @DisplayName("Fehlende Angaben beim Verhaengen werfen - das ist ein Rechenfehler")
    void fehlendeAngabenWerfen() {
        Strafbuch buch = buch();

        assertThrows(IllegalArgumentException.class, () -> buch.verhaenge(null,
                Strafart.BANN, "Grund", EINE_STUNDE, ANNA, VERWALTER, MITTAGS));
        assertThrows(IllegalArgumentException.class, () -> buch.verhaenge(BERT,
                null, "Grund", EINE_STUNDE, ANNA, VERWALTER, MITTAGS));
        assertThrows(IllegalArgumentException.class, () -> buch.verhaenge(BERT,
                Strafart.BANN, "Grund", EINE_STUNDE, ANNA, VERWALTER, null));
        assertThrows(IllegalArgumentException.class, () -> buch.verhaenge(BERT,
                Strafart.BANN, "Grund", null, ANNA, VERWALTER, MITTAGS),
                "ein Bann ohne Dauer waere stillschweigend unbegrenzt - und genau das"
                        + " braucht ein Recht");
        assertThrows(IllegalArgumentException.class, () -> buch.verhaenge(BERT,
                Strafart.RAUSWURF, "Grund", EINE_STUNDE, ANNA, VERWALTER, MITTAGS));
    }

    // -------------------------------------------------------------------- aufheben

    @Test
    @DisplayName("Ein Moderator hebt einen befristeten Bann auf")
    void moderatorHebtBefristetAuf() {
        Strafbuch buch = buch();
        long nummer = buch.verhaenge(BERT, Strafart.BANN, "Grief", EINE_STUNDE,
                ANNA, MODERATOR, MITTAGS).strafe().orElseThrow().nummer();

        Strafbuch.Entscheid entscheid = buch.hebeAuf(nummer, CLARA,
                "Einspruch angenommen", SPAETER, MODERATOR);

        assertEquals(Strafbuch.Bescheid.AUFGEHOBEN, entscheid.bescheid());
        assertTrue(entscheid.geaendert());
        assertTrue(buch.geltende(BERT, Strafart.BANN, SPAETER).isEmpty());
        assertEquals("Einspruch angenommen",
                buch.lies(nummer).orElseThrow().aufhebungsgrund());
    }

    @Test
    @DisplayName("Eine UNBEGRENZTE aufzuheben braucht das Recht fuer Dauerhaftes")
    void unbegrenzteAufhebenBrauchtDasRecht() {
        // Sonst nimmt ein Moderator den dauerhaften Bann zurueck, den ein Verwalter
        // verhaengt hat - und die Unterscheidung aus Plan 12c.2 waere in der einen
        // Richtung gesichert und in der anderen offen.
        Strafbuch buch = buch();
        long nummer = buch.verhaenge(BERT, Strafart.BANN, "fuer immer",
                Strafdauer.UNBEGRENZT, ANNA, VERWALTER, MITTAGS).strafe().orElseThrow()
                .nummer();

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.hebeAuf(nummer, CLARA, "zu grosszuegig", SPAETER, MODERATOR)
                        .bescheid());
        assertEquals(Strafbuch.Bescheid.AUFGEHOBEN,
                buch.hebeAuf(nummer, CLARA, "Einspruch angenommen", SPAETER, VERWALTER)
                        .bescheid());
    }

    @Test
    @DisplayName("Ohne das Recht zum Aufheben geht nichts")
    void ohneAufhebenrechtGehtNichts() {
        Strafbuch buch = buch();
        long nummer = buch.verhaenge(BERT, Strafart.BANN, "Grief", EINE_STUNDE,
                ANNA, MODERATOR, MITTAGS).strafe().orElseThrow().nummer();

        // Wer bannen darf, darf nicht von selbst entbannen: Ein Moderator, der seine
        // eigene Strafe zurueckholen kann, kann sie auch zur Drohung machen.
        assertEquals(Strafbuch.Bescheid.OHNE_RECHT, buch.hebeAuf(nummer, CLARA,
                "Einspruch", SPAETER, Set.of(NAMEN.bann())).bescheid());
    }

    @Test
    @DisplayName("Eine Aufhebung OHNE Grund wird abgewiesen")
    void aufhebungBrauchtGrund() {
        Strafbuch buch = buch();
        long nummer = buch.verhaenge(BERT, Strafart.BANN, "Grief", EINE_STUNDE,
                ANNA, MODERATOR, MITTAGS).strafe().orElseThrow().nummer();

        assertEquals(Strafbuch.Bescheid.GRUND_UNBRAUCHBAR,
                buch.hebeAuf(nummer, CLARA, "", SPAETER, MODERATOR).bescheid());
        assertEquals(Strafbuch.Bescheid.GRUND_UNBRAUCHBAR,
                buch.hebeAuf(nummer, CLARA, "ab", SPAETER, MODERATOR).bescheid());
        assertEquals(Strafbuch.Bescheid.GRUND_UNBRAUCHBAR,
                buch.hebeAuf(nummer, CLARA, null, SPAETER, MODERATOR).bescheid());
    }

    @Test
    @DisplayName("NIEMAND hebt seine EIGENE Strafe auf")
    void niemandHebtSichSelbstAuf() {
        // DER SCHWERSTE FUND VOM 25.11.2026. verhaenge hatte diesen Riegel, hebeAuf
        // nicht: Ein stummgeschalteter Moderator mit dem Recht zum Aufheben tippte
        // /strafe verlauf auf sich selbst, las seine Nummer, hob auf - und die
        // Ansage sagte allen, er sei "wieder frei".
        Strafbuch buch = buch();
        long nummer = buch.verhaenge(BERT, Strafart.STUMM, "Beleidigung", EINE_STUNDE,
                ANNA, VERWALTER, MITTAGS).strafe().orElseThrow().nummer();

        Strafbuch.Entscheid entscheid = buch.hebeAuf(nummer, BERT, "finde ich hart",
                SPAETER, MODERATOR);

        assertEquals(Strafbuch.Bescheid.SELBST, entscheid.bescheid());
        assertTrue(buch.geltende(BERT, Strafart.STUMM, SPAETER).isPresent(),
                "die Strafe steht weiter");
        // Und ein ANDERER darf es weiter - der Riegel trifft nur den Betroffenen.
        assertEquals(Strafbuch.Bescheid.AUFGEHOBEN,
                buch.hebeAuf(nummer, CLARA, "Einspruch angenommen", SPAETER,
                        MODERATOR).bescheid());
    }

    @Test
    @DisplayName("Die Strafe eines TEAMMITGLIEDS hebt nur das Team auf")
    void teamstrafeHebtNurDasTeamAuf() {
        // Sonst nimmt ein Moderator die Strafe zurueck, die ein Verwalter gegen einen
        // Kollegen verhaengt hat - und der Verwalter koennte einen Moderator nur
        // dauerhaft strafen, wenn es halten soll.
        Strafbuch buch = buch();
        imTeam.add(BERT);
        long nummer = buch.verhaenge(BERT, Strafart.STUMM, "Beleidigung", EINE_STUNDE,
                ANNA, VERWALTER, MITTAGS).strafe().orElseThrow().nummer();

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_TEAM,
                buch.hebeAuf(nummer, CLARA, "Kollegenhilfe", SPAETER, MODERATOR)
                        .bescheid());
        assertEquals(Strafbuch.Bescheid.AUFGEHOBEN,
                buch.hebeAuf(nummer, CLARA, "Einspruch angenommen", SPAETER,
                        VERWALTER).bescheid(),
                "ein Verwalter darf es");
    }

    @Test
    @DisplayName("DIESELBE Grenze beim Aufheben wie beim Verhaengen")
    void dieselbeGrenzeBeimAufheben() {
        // DER FUND: Das Aufheben fragte nur unbegrenzt(), das Verhaengen aber auch
        // "laenger als die Hoechstdauer". Ein Moderator nahm damit den
        // Zehn-Jahres-Bann eines Verwalters zurueck - die Unterscheidung aus Plan
        // 12c.2 war in der einen Richtung gesichert und in der anderen offen.
        Strafbuch buch = buch(Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN,
                720L));
        long lang = buch.verhaenge(BERT, Strafart.BANN, "zehn Jahre",
                new Strafdauer(Duration.ofDays(3650L)), ANNA, VERWALTER, MITTAGS)
                .strafe().orElseThrow().nummer();
        long kurz = buch.verhaenge(CLARA, Strafart.BANN, "eine Stunde", EINE_STUNDE,
                ANNA, MODERATOR, MITTAGS).strafe().orElseThrow().nummer();

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT,
                buch.hebeAuf(lang, ANNA, "zu hart", SPAETER, MODERATOR).bescheid(),
                "eine Strafe ueber der Hoechstdauer hebt nur auf, wer sie auch"
                        + " verhaengen duerfte");
        assertEquals(Strafbuch.Bescheid.AUFGEHOBEN,
                buch.hebeAuf(kurz, ANNA, "Einspruch angenommen", SPAETER, MODERATOR)
                        .bescheid(),
                "eine kurze darf jeder aufheben, der aufheben darf");
        assertEquals(Strafbuch.Bescheid.AUFGEHOBEN,
                buch.hebeAuf(lang, ANNA, "Einspruch angenommen", SPAETER, VERWALTER)
                        .bescheid(),
                "und der Verwalter darf auch die lange");
    }

    @Test
    @DisplayName("Eine unbekannte Nummer gibt UNBEKANNT")
    void unbekannteNummer() {
        assertEquals(Strafbuch.Bescheid.UNBEKANNT,
                buch().hebeAuf(9999L, ANNA, "Einspruch", MITTAGS, VERWALTER)
                        .bescheid());
    }

    @Test
    @DisplayName("Ein RAUSWURF laesst sich nicht aufheben")
    void rauswurfNichtAufhebbar() {
        Strafbuch buch = buch();
        long nummer = buch.verhaenge(BERT, Strafart.RAUSWURF, "Spam", null, ANNA,
                MODERATOR, MITTAGS).strafe().orElseThrow().nummer();

        Strafbuch.Entscheid entscheid = buch.hebeAuf(nummer, CLARA, "unnoetig", SPAETER,
                VERWALTER);

        assertEquals(Strafbuch.Bescheid.NICHT_FORTWIRKEND, entscheid.bescheid());
        assertTrue(entscheid.strafe().isPresent(),
                "die Antwort nennt sie - sonst glaubt der Moderator, er habe sich"
                        + " in der Nummer geirrt");
    }

    @Test
    @DisplayName("Ein ZWEITES Aufheben gibt SCHON_AUFGEHOBEN und nennt den ersten Grund")
    void zweitesAufhebenAbgewiesen() {
        Strafbuch buch = buch();
        long nummer = buch.verhaenge(BERT, Strafart.BANN, "Grief", EINE_STUNDE,
                ANNA, MODERATOR, MITTAGS).strafe().orElseThrow().nummer();
        buch.hebeAuf(nummer, ANNA, "erster Grund", SPAETER, MODERATOR);

        Strafbuch.Entscheid zweiter = buch.hebeAuf(nummer, CLARA, "zweiter Grund",
                MITTAGS.plusSeconds(120L), MODERATOR);

        assertEquals(Strafbuch.Bescheid.SCHON_AUFGEHOBEN, zweiter.bescheid());
        assertEquals("erster Grund", zweiter.strafe().orElseThrow().aufhebungsgrund());
        assertFalse(zweiter.geaendert());
    }

    @Test
    @DisplayName("Aufheben ohne Zeitpunkt wirft")
    void aufhebenOhneZeitWirft() {
        Strafbuch buch = buch();

        assertThrows(IllegalArgumentException.class,
                () -> buch.hebeAuf(1L, ANNA, "Einspruch", null, VERWALTER));
    }

    // --------------------------------------------------------------------- Auskunft

    @Test
    @DisplayName("geltende fragt OHNE Rechte - eine Wache fragt, sie entscheidet nicht")
    void geltendeOhneRechte() {
        Strafbuch buch = buch();
        buch.verhaenge(BERT, Strafart.BANN, "Grief", EINE_STUNDE, ANNA,
                MODERATOR, MITTAGS);

        // Die Wache fragt bei JEDEM Beitritt. Ein Riegel dort waere einer, der den
        // Netzfaden Rechte nachsehen liesse - und die Antwort haengt nicht davon ab,
        // wer fragt.
        assertTrue(buch.geltende(BERT, Strafart.BANN, SPAETER).isPresent());
    }

    @Test
    @DisplayName("geltende reicht eine Stoerung DURCH und verschluckt sie nicht")
    void geltendeReichtStoerungDurch() {
        Strafbuch buch = new Strafbuch(new WerfendeAblage(), () -> Strafwerte.VORGABE,
                NAMEN, imTeam::contains);

        // Die andere Richtung als bei verhaenge, und das ist Absicht: Wer nicht weiss,
        // ob ein Spieler gebannt ist, darf ihn nicht stillschweigend hereinlassen. Ein
        // leeres Optional hier waere genau das.
        assertThrows(IllegalStateException.class,
                () -> buch.geltende(BERT, Strafart.BANN, MITTAGS));
    }

    @Test
    @DisplayName("Die Vorgeschichte haelt die Seitenlaenge aus der Datei ein")
    void vorgeschichteHaeltSeitenlaenge() {
        Strafbuch buch = buch(Strafwerte.VORGABE.mitZeilenJeSeite(2));
        for (int lauf = 0; lauf < 5; lauf++) {
            buch.verhaenge(BERT, Strafart.RAUSWURF, "Vorfall " + lauf, null,
                    ANNA, MODERATOR, MITTAGS.plusSeconds(lauf * 60L));
        }

        List<Strafe> verlauf = buch.vorgeschichte(BERT);

        assertEquals(2, verlauf.size());
        assertEquals("Vorfall 4", verlauf.get(0).grund(), "die neuesten zuerst");
    }

    @Test
    @DisplayName("Die Bannliste zeigt nur BANNE - keine Stummschaltungen")
    void bannlisteNurBanne() {
        Strafbuch buch = buch();
        buch.verhaenge(BERT, Strafart.BANN, "gebannt", EINE_STUNDE, ANNA,
                MODERATOR, MITTAGS);
        buch.verhaenge(CLARA, Strafart.STUMM, "stumm", EINE_STUNDE, ANNA,
                MODERATOR, MITTAGS);

        List<Strafe> liste = buch.bannliste(SPAETER);

        // Eine oeffentliche Liste der Stummgeschalteten waere ein Pranger fuer eine
        // Strafe, die in zehn Minuten vorbei ist. Plan 12c.4 nennt die Bannliste.
        assertEquals(1, liste.size());
        assertEquals("gebannt", liste.get(0).grund());
    }

    @Test
    @DisplayName("Die Werte kommen bei JEDEM Zugriff neu - die Datei ist neuladbar")
    void werteKommenNeu() {
        // Ein hier festgehaltenes Exemplar waere nach dem ersten Neuladen die alte
        // Datei - und der Wert, der sich aendert, ist die Grenze zwischen dem, was ein
        // Moderator darf, und dem, was ein Verwalter darf.
        Strafwerte[] stand = {Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN,
                720L)};
        Strafbuch buch = new Strafbuch(new SpeicherStrafen(), () -> stand[0], NAMEN,
                imTeam::contains);
        Strafdauer zweiTage = new Strafdauer(Duration.ofDays(2L));

        assertEquals(Strafbuch.Bescheid.VERHAENGT, buch.verhaenge(BERT,
                Strafart.BANN, "zwei Tage", zweiTage, ANNA, MODERATOR, MITTAGS)
                .bescheid());

        stand[0] = Strafwerte.VORGABE.mitHoechsteStunden(Strafart.BANN, 24L);

        assertEquals(Strafbuch.Bescheid.OHNE_RECHT_DAUERHAFT, buch.verhaenge(CLARA, Strafart.BANN, "zwei Tage", zweiTage, ANNA, MODERATOR, MITTAGS)
                .bescheid());
    }

    @Test
    @DisplayName("Ein null-Lieferant gibt die Vorgabe und keinen Wurf")
    void nullLieferantGibtVorgabe() {
        Strafbuch buch = new Strafbuch(new SpeicherStrafen(), () -> null, NAMEN,
                imTeam::contains);

        assertEquals(Strafwerte.VORGABE, buch.werte());
    }

    @Test
    @DisplayName("Ohne Ablage, Werte oder Rechtenamen gibt es kein Buch")
    void fehlendeBauteileWerfen() {
        assertThrows(IllegalArgumentException.class, () -> new Strafbuch(null,
                () -> Strafwerte.VORGABE, NAMEN, imTeam::contains));
        assertThrows(IllegalArgumentException.class, () -> new Strafbuch(
                new SpeicherStrafen(), null, NAMEN, imTeam::contains));
        assertThrows(IllegalArgumentException.class, () -> new Strafbuch(
                new SpeicherStrafen(), () -> Strafwerte.VORGABE, null,
                imTeam::contains));
        // Ohne die Teamfrage waere JEDES Teammitglied ungeschuetzt - und zwar in
        // beide Richtungen, beim Strafen und beim Aufheben.
        assertThrows(IllegalArgumentException.class, () -> new Strafbuch(
                new SpeicherStrafen(), () -> Strafwerte.VORGABE, NAMEN, null));
    }

    @Test
    @DisplayName("SCHON_GESTRAFT ohne die geltende Strafe kann nicht entstehen")
    void schonGestraftBrauchtDieStrafe() {
        // Die Absage muss die NUMMER nennen - sonst weiss der Moderator nicht, welche
        // er aufheben soll, und der Weg aus der Absage heraus ist verschlossen.
        assertThrows(IllegalArgumentException.class,
                () -> new Strafbuch.Entscheid(Strafbuch.Bescheid.SCHON_GESTRAFT,
                        Optional.empty(), 0L));
    }

    /** Eine Ablage, die bei jedem Zugriff wirft - fuer den Bescheid GESTOERT. */
    private static final class WerfendeAblage implements Strafablage {

        @Override
        public Strafe lege(UUID betroffener, Strafart art, String grund, UUID vonWem,
                           Instant verhaengtAm, Strafdauer dauer) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public Optional<Strafe> lies(long nummer) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public boolean hebeAuf(Strafe geaendert) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public Optional<Strafe> geltende(UUID betroffener, Strafart art, Instant jetzt) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public List<Strafe> von(UUID betroffener, int hoechstens) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public List<Strafe> geltendeAlle(Strafart art, int hoechstens, Instant jetzt) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }

        @Override
        public int zaehle(UUID betroffener, Strafart art) {
            throw new IllegalStateException("die Datenbank antwortet nicht");
        }
    }
}
