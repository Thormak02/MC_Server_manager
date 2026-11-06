package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Berufsangabe;
import de.freimarken.kern.Berufsstand;
import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.SpeicherBerufe;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /beruf} GETIPPT - durch den echten Befehlsbaum, mit echter Ablage und echter
 * Kasse.
 *
 * <p>Gleiche Bauart wie {@link FreimarkenBefehlTest} und {@link ParzelleBefehlTest}: Der
 * Baum wird gebaut, der Befehl getippt, und geprueft wird, was beim Spieler ANKOMMT. Keine
 * dieser Proben ruft eine Methode von {@link BerufeBefehle} unmittelbar auf - sonst waere
 * genau der Weg ungeprueft, auf dem ein Spieler den Befehl benutzt.
 *
 * <p><b>Was nur hier geprueft werden kann</b>, ist die Verdrahtung: dass das Recht fuer
 * Spezialisierungen am SENDER gefragt wird, dass die Rueckfrage vor dem bezahlten Ablegen
 * steht, dass erst gebucht und dann geschrieben wird - und dass die Erstattung kommt, wenn
 * das Schreiben scheitert. Die REGEL selbst haelt {@code BerufsregelTest} ohne Server, und
 * die ZEILEN halten die Bildproben.
 */
class BerufBefehlTest {

    /** Ein fester Zeitpunkt - der Befehl bekommt seine Uhr von aussen. */
    private static final Instant MITTAGS = Instant.parse("2026-11-06T12:00:00Z");

    private static final long REICH = 100_000L;

    /** Zwei Grundberufe, eine Spezialisierung - wie die Vorgabe es erlaubt. */
    private static final Berufsverzeichnis VERZEICHNIS = Berufsverzeichnis.ausAngaben(
            List.of(new Berufsangabe("bergmann", null),
                    new Berufsangabe("bauer", null),
                    new Berufsangabe("steinmetz", "bergmann")));

    private Befehlsprobe baum;
    private Berufsablage ablage;
    private Kasse kasse;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        ablage = new SpeicherBerufe();
        kasse = new SpeicherKasse(REICH);
        baum = new Befehlsprobe(List.of(befehle(ablage, kasse, Berufswerte.VORGABE)));
    }

    private static de.freimarken.paper.BerufeBefehle bauen(Berufsablage ablage,
                                                           Kasse kasse,
                                                           Berufswerte werte) {
        return new BerufeBefehle(ablage, VERZEICHNIS,
                new Wirtschaftsstand(new Wirtschaftswerte(Preisstaffel.VORGABE,
                        REICH, Wirtschaftswerte.VORGABE_HOECHSTZAHL,
                        Wirtschaftswerte.VORGABE_GRUPPEN_FELDER,
                        Wirtschaftswerte.VORGABE_RANG_WERBUNG, Zeitwerte.VORGABE, werte)),
                kasse, Clock.fixed(MITTAGS, ZoneOffset.UTC),
                Logger.getLogger(BerufBefehlTest.class.getName()),
                Sprachprobe.spracheAusDemJar());
    }

    private static com.mojang.brigadier.tree.LiteralCommandNode<
            io.papermc.paper.command.brigadier.CommandSourceStack> befehle(
                    Berufsablage ablage, Kasse kasse, Berufswerte werte) {
        return bauen(ablage, kasse, werte).beruf();
    }

    private static Spielerprobe spieler(int zahl, String... rechte) {
        return new Spielerprobe("Cara", Spielerprobe.kennungAus(zahl), rechte);
    }

    /** Ein Spieler mit dem Grundrecht - das hat jeder. */
    private static Spielerprobe jeder(int zahl) {
        return spieler(zahl, BerufeBefehle.RECHT);
    }

    /** Und einer, der auch Spezialisierungen halten darf. */
    private static Spielerprobe buerger(int zahl) {
        return spieler(zahl, BerufeBefehle.RECHT,
                BerufeBefehle.RECHT_SPEZIALISIERUNG);
    }

    /**
     * Eine Ablage, die liest wie die echte und beim Ablegen immer nein sagt.
     *
     * <p>Ein Stellvertreter und keine Unterklasse: {@link SpeicherBerufe} ist
     * {@code final}, und das soll es bleiben - eine Ablage, von der man erben kann, ist
     * eine Ablage, deren Zusagen eine Unterklasse still aufheben kann.
     */
    private record SchreibtNicht(Berufsablage echt) implements Berufsablage {
        @Override
        public Berufsstand lies(UUID spieler) {
            return echt.lies(spieler);
        }

        @Override
        public boolean waehle(Antrag antrag, Berufsstand erwartet) {
            return echt.waehle(antrag, erwartet);
        }

        @Override
        public boolean verlasse(Antrag antrag, Berufsstand erwartet) {
            return false;
        }
    }

    /** Eine Ablage, die gar nicht antwortet - wie eine abgerissene Verbindung. */
    private record LiestNicht() implements Berufsablage {
        @Override
        public Berufsstand lies(UUID spieler) {
            throw new IllegalStateException("Datenbank weg");
        }

        @Override
        public boolean waehle(Antrag antrag, Berufsstand erwartet) {
            throw new IllegalStateException("Datenbank weg");
        }

        @Override
        public boolean verlasse(Antrag antrag, Berufsstand erwartet) {
            throw new IllegalStateException("Datenbank weg");
        }
    }

    private void waehle(UUID wer, String beruf) {
        assertTrue(ablage.waehle(new Berufsablage.Antrag(wer, beruf, MITTAGS),
                ablage.lies(wer)), "die Vorbereitung muss durchgehen: " + beruf);
    }

    // --- Die Liste -------------------------------------------------------------

    @Test
    @DisplayName("45 - /beruf liste zeigt jeden Grundberuf mit seinen Spezialisierungen")
    void listeZeigtAlles() throws CommandSyntaxException {
        Spielerprobe cara = jeder(1);

        assertEquals(1, baum.tippe("beruf liste", cara));

        assertEquals(List.of("&6Berufe im Freimarkenbund",
                        "&7  &fBergmann&7 · dazu: Steinmetz",
                        "&7  &fBauer",
                        "&7  /beruf waehlen <beruf> · /beruf stand"),
                cara.chatBild());
    }

    @Test
    @DisplayName("45b - /beruf liste geht auch auf der Konsole")
    void listeAufDerKonsole() throws CommandSyntaxException {
        // Die Liste haengt an keinem Spieler, sondern an der Einstellungsdatei. Sie ist
        // der einzige Zweig, der nicht durch die Spielerpruefung laeuft - und das soll so
        // bleiben: Der Betreiber will sie lesen koennen, ohne sich einzuloggen.
        assertEquals(1, baum.tippeAufDerKonsole("beruf liste"));

        assertEquals(List.of("&6Berufe im Freimarkenbund",
                        "&7  &fBergmann&7 · dazu: Steinmetz",
                        "&7  &fBauer",
                        "&7  /beruf waehlen <beruf> · /beruf stand"),
                baum.konsoleBild());
    }

    @Test
    @DisplayName("45c - jeder andere Zweig sagt der Konsole, dass sie keinen Beruf hat")
    void konsoleHatKeinenBeruf() throws CommandSyntaxException {
        assertEquals(1, baum.tippeAufDerKonsole("beruf"));
        assertEquals(1, baum.tippeAufDerKonsole("beruf stand"));
        assertEquals(1, baum.tippeAufDerKonsole("beruf waehlen bergmann"));
        assertEquals(1, baum.tippeAufDerKonsole("beruf verlassen bergmann"));

        // Viermal derselbe Satz und keine Ausnahme: Ein spielerpflichtiger Befehl
        // antwortet auf der Konsole sonst wortgleich wie ein Befehl, den es nicht gibt.
        assertEquals(List.of(
                        "&7Ein Beruf gehoert zu einem Spieler - die Konsole hat keinen.",
                        "&7Ein Beruf gehoert zu einem Spieler - die Konsole hat keinen.",
                        "&7Ein Beruf gehoert zu einem Spieler - die Konsole hat keinen.",
                        "&7Ein Beruf gehoert zu einem Spieler - die Konsole hat keinen."),
                baum.konsoleBild());
        assertEquals(List.of(), ablage.lies(UUID.randomUUID()).berufe(),
                "und geschrieben wurde nichts");
    }

    // --- Der Stand -------------------------------------------------------------

    @Test
    @DisplayName("45d - das nackte /beruf zeigt den Stand, nicht die Liste")
    void nacktesBerufZeigtDenStand() throws CommandSyntaxException {
        Spielerprobe cara = jeder(2);

        assertEquals(1, baum.tippe("beruf", cara));

        assertEquals(List.of("&6Dein Beruf",
                        "&7Du hast noch keinen Beruf gewaehlt.",
                        "&7  Grundberufe:       0 von 2",
                        "&7  Spezialisierungen: 0 von 1",
                        "&7  Ein Beruf aufgeben kostet 5.000 Mark."),
                cara.chatBild(),
                "wer /beruf ohne Zusatz tippt, will wissen, was er selbst ist");
    }

    @Test
    @DisplayName("45e - der Stand nennt Grundberuf und Spezialisierung verschieden")
    void standNenntBeideRollen() throws CommandSyntaxException {
        Spielerprobe cara = buerger(3);
        waehle(cara.kennung(), "bergmann");
        waehle(cara.kennung(), "steinmetz");

        assertEquals(1, baum.tippe("beruf stand", cara));

        assertEquals(List.of("&6Dein Beruf",
                        "&7  &fBergmann",
                        "&7  &fSteinmetz&7 · baut auf Bergmann auf",
                        "&7  Grundberufe:       1 von 2",
                        "&7  Spezialisierungen: 1 von 1",
                        "&7  Ein Beruf aufgeben kostet 5.000 Mark."),
                cara.chatBild());
    }

    @Test
    @DisplayName("45f - eine laufende Sperrfrist steht im Stand, mit der Restzeit")
    void standNenntDieRestfrist() throws CommandSyntaxException {
        Spielerprobe cara = jeder(4);
        waehle(cara.kennung(), "bergmann");
        // Vor zehn Stunden abgelegt, Frist 24 Stunden - 14 bleiben.
        ablage.verlasse(new Berufsablage.Antrag(cara.kennung(), "bergmann",
                MITTAGS.minus(Duration.ofHours(10L))), ablage.lies(cara.kennung()));

        assertEquals(1, baum.tippe("beruf stand", cara));

        assertTrue(cara.chatBild().contains(
                        "&e  Du kannst in 14 h 00 min wieder wechseln."),
                cara.chatBild().toString());
        // Und die Zeile "noch keinen gewaehlt" steht NICHT da: Wer alles abgelegt hat,
        // ist kein Neuling.
        assertFalse(cara.chatBild().contains("&7Du hast noch keinen Beruf gewaehlt."),
                cara.chatBild().toString());
    }

    // --- Waehlen ---------------------------------------------------------------

    @Test
    @DisplayName("45g - /beruf waehlen traegt ein und sagt es - ohne Geld")
    void waehlenTraegtEin() throws CommandSyntaxException {
        Spielerprobe cara = jeder(5);

        assertEquals(1, baum.tippe("beruf waehlen bergmann", cara));

        assertEquals(List.of("&aDu bist jetzt Bergmann."), cara.chatBild());
        assertEquals(List.of("bergmann"), ablage.lies(cara.kennung()).berufe());
        assertEquals(REICH, kasse.guthaben(cara.kennung()),
                "Waehlen kostet nichts - bezahlt wird das Aufgeben");
        assertNull(ablage.lies(cara.kennung()).letztesAblegen(),
                "und es beginnt keine Sperrfrist");
    }

    @Test
    @DisplayName("45h - gross getippt ist derselbe Beruf")
    void grossGetippt() throws CommandSyntaxException {
        Spielerprobe cara = jeder(6);

        assertEquals(1, baum.tippe("beruf waehlen BERGMANN", cara));

        assertEquals(List.of("&aDu bist jetzt Bergmann."), cara.chatBild());
        assertEquals(List.of("bergmann"), ablage.lies(cara.kennung()).berufe());
    }

    @Test
    @DisplayName("45i - ein Beruf, den es nicht gibt: die Absage nennt die Eingabe")
    void unbekannterBeruf() throws CommandSyntaxException {
        Spielerprobe cara = jeder(7);

        assertEquals(1, baum.tippe("beruf waehlen Holzfaeler", cara));

        assertEquals(List.of("&cDen Beruf \"Holzfaeler\" gibt es nicht."
                        + " /beruf liste zeigt alle."), cara.chatBild());
        assertEquals(List.of(), ablage.lies(cara.kennung()).berufe());
    }

    @Test
    @DisplayName("45j - OHNE das Recht gibt es keine Spezialisierung, und zwar mit Grund")
    void spezialisierungOhneRecht() throws CommandSyntaxException {
        // Das Recht wird am SENDER gefragt und nicht in requires: Ein requires am
        // Argument verstecke den ganzen Zweig, und der Spieler laese "unbekannter Befehl"
        // statt des Grundes. Genau diese Verdrahtung prueft nur diese Probe.
        Spielerprobe cara = jeder(8);
        waehle(cara.kennung(), "bergmann");

        assertEquals(1, baum.tippe("beruf waehlen steinmetz", cara));

        assertEquals(List.of("&cSpezialisierungen sind noch nicht fuer dich"
                        + " freigegeben."), cara.chatBild());
        assertEquals(List.of("bergmann"), ablage.lies(cara.kennung()).berufe());
    }

    @Test
    @DisplayName("45k - MIT dem Recht und mit dem Grundberuf geht sie durch")
    void spezialisierungMitRecht() throws CommandSyntaxException {
        Spielerprobe cara = buerger(9);
        waehle(cara.kennung(), "bergmann");

        assertEquals(1, baum.tippe("beruf waehlen steinmetz", cara));

        assertEquals(List.of("&aDu bist jetzt Steinmetz."), cara.chatBild());
        assertEquals(List.of("bergmann", "steinmetz"),
                ablage.lies(cara.kennung()).berufe());
    }

    @Test
    @DisplayName("45l - mit dem Recht, aber ohne den Grundberuf: die Absage nennt ihn")
    void grundberufFehlt() throws CommandSyntaxException {
        Spielerprobe cara = buerger(10);

        assertEquals(1, baum.tippe("beruf waehlen steinmetz", cara));

        assertEquals(List.of("&cDafuer musst du zuerst Bergmann sein."), cara.chatBild());
    }

    @Test
    @DisplayName("45m - sind die Grundberufe voll, nennt die Absage die Platzgrenze")
    void grundberufeVoll() throws CommandSyntaxException {
        Spielerprobe cara = jeder(11);
        waehle(cara.kennung(), "bergmann");
        waehle(cara.kennung(), "bauer");

        assertEquals(1, baum.tippe("beruf waehlen bergmann", cara),
                "derselbe Beruf: das ist schon-gehalten und nicht voll");
        assertEquals(List.of("&7Du bist schon Bergmann."), cara.chatBild());
    }

    // --- Verlassen -------------------------------------------------------------

    @Test
    @DisplayName("45n - /beruf verlassen fragt erst zurueck und bucht NICHT")
    void verlassenFragtZurueck() throws CommandSyntaxException {
        Spielerprobe cara = jeder(12);
        waehle(cara.kennung(), "bergmann");

        assertEquals(1, baum.tippe("beruf verlassen bergmann", cara));

        assertEquals(List.of("&eBergmann aufgeben?",
                        "&7  Es kostet &65.000 Mark&7, und danach kannst du 24 h 00 min"
                                + " lang nicht wechseln.",
                        "&c  /beruf verlassen bergmann bestaetigen"),
                cara.chatBild());
        // Das Wichtigste an dieser Probe: NICHTS ist passiert.
        assertEquals(List.of("bergmann"), ablage.lies(cara.kennung()).berufe());
        assertEquals(REICH, kasse.guthaben(cara.kennung()));
    }

    @Test
    @DisplayName("45o - mit bestaetigen wird gebucht UND geschrieben")
    void verlassenMitBestaetigung() throws CommandSyntaxException {
        Spielerprobe cara = jeder(13);
        waehle(cara.kennung(), "bergmann");

        assertEquals(1, baum.tippe("beruf verlassen bergmann bestaetigen", cara));

        assertEquals(List.of("&7Du hast Bergmann aufgegeben."
                        + " Gebucht: &65.000 Mark&7."), cara.chatBild());
        assertEquals(List.of(), ablage.lies(cara.kennung()).berufe());
        assertEquals(REICH - 5_000L, kasse.guthaben(cara.kennung()));
        assertEquals(MITTAGS, ablage.lies(cara.kennung()).letztesAblegen(),
                "und die Sperrfrist beginnt JETZT");
    }

    @Test
    @DisplayName("45p - kostet ein Wechsel nichts, faellt die Rueckfrage weg")
    void ohneGebuehrKeineRueckfrage() throws CommandSyntaxException {
        // Eine Sicherung vor einer Tat ohne Preis waere nur ein zweiter Tastendruck. Die
        // Sperrfrist allein ist kein Grund fuer eine Rueckfrage: Wer aufgibt, will
        // aufgeben.
        Berufsablage eigene = new SpeicherBerufe();
        Kasse eigeneKasse = new SpeicherKasse(REICH);
        Befehlsprobe gratis = new Befehlsprobe(List.of(befehle(eigene, eigeneKasse,
                new Berufswerte(0L, Duration.ofHours(24L), 2, 1))));
        Spielerprobe cara = jeder(14);
        assertTrue(eigene.waehle(new Berufsablage.Antrag(cara.kennung(), "bergmann",
                MITTAGS), eigene.lies(cara.kennung())));

        assertEquals(1, gratis.tippe("beruf verlassen bergmann", cara));

        assertEquals(List.of("&7Du hast Bergmann aufgegeben."
                        + " Gebucht: &60 Mark&7."), cara.chatBild());
        assertEquals(List.of(), eigene.lies(cara.kennung()).berufe());
        assertEquals(REICH, eigeneKasse.guthaben(cara.kennung()),
                "und gebucht wurde nichts - 0 Mark bucht der Befehl nicht");
    }

    @Test
    @DisplayName("45q - fehlt das Geld, wird nichts angefasst")
    void geldFehlt() throws CommandSyntaxException {
        Berufsablage eigene = new SpeicherBerufe();
        Kasse arm = new SpeicherKasse(100L);
        Befehlsprobe mitArmerKasse = new Befehlsprobe(List.of(befehle(eigene, arm,
                Berufswerte.VORGABE)));
        Spielerprobe cara = jeder(15);
        assertTrue(eigene.waehle(new Berufsablage.Antrag(cara.kennung(), "bergmann",
                MITTAGS), eigene.lies(cara.kennung())));

        assertEquals(1, mitArmerKasse.tippe("beruf verlassen bergmann bestaetigen", cara));

        assertEquals(List.of("&cAufgeben kostet 5.000 Mark, du hast 100 Mark."),
                cara.chatBild());
        assertEquals(List.of("bergmann"), eigene.lies(cara.kennung()).berufe(),
                "der Beruf bleibt");
        assertEquals(100L, arm.guthaben(cara.kennung()), "und das Geld auch");
    }

    @Test
    @DisplayName("45r - die Sperrfrist steht vor dem Geld")
    void fristVorDemGeld() throws CommandSyntaxException {
        Berufsablage eigene = new SpeicherBerufe();
        Kasse arm = new SpeicherKasse(0L);
        Befehlsprobe mitArmerKasse = new Befehlsprobe(List.of(befehle(eigene, arm,
                Berufswerte.VORGABE)));
        Spielerprobe cara = jeder(16);
        assertTrue(eigene.waehle(new Berufsablage.Antrag(cara.kennung(), "bergmann",
                MITTAGS), eigene.lies(cara.kennung())));
        assertTrue(eigene.waehle(new Berufsablage.Antrag(cara.kennung(), "bauer",
                MITTAGS), eigene.lies(cara.kennung())));
        eigene.verlasse(new Berufsablage.Antrag(cara.kennung(), "bauer",
                MITTAGS.minus(Duration.ofHours(6L))), eigene.lies(cara.kennung()));

        assertEquals(1, mitArmerKasse.tippe("beruf verlassen bergmann bestaetigen", cara));

        // Beides fehlt - Geld UND Zeit. Gemeldet wird die Zeit: Geld laesst sich
        // verdienen, Zeit nicht.
        assertEquals(List.of("&cDu kannst erst in 18 h 00 min wieder wechseln."),
                cara.chatBild());
        assertEquals(List.of("bergmann"), eigene.lies(cara.kennung()).berufe());
    }

    @Test
    @DisplayName("45s - ein Grundberuf bleibt, solange seine Spezialisierung steht")
    void grundberufTraegtSpezialisierung() throws CommandSyntaxException {
        Spielerprobe cara = buerger(17);
        waehle(cara.kennung(), "bergmann");
        waehle(cara.kennung(), "steinmetz");

        assertEquals(1, baum.tippe("beruf verlassen bergmann bestaetigen", cara));

        assertEquals(List.of("&cDafuer musst du zuerst Steinmetz aufgeben."),
                cara.chatBild());
        assertEquals(List.of("bergmann", "steinmetz"),
                ablage.lies(cara.kennung()).berufe());
        assertEquals(REICH, kasse.guthaben(cara.kennung()), "und gebucht wurde nichts");
    }

    @Test
    @DisplayName("45t - scheitert das Schreiben, kommt die Gebuehr zurueck")
    void erstattungBeimFehlschlag() throws CommandSyntaxException {
        // Die Reihenfolge ist erst buchen, dann schreiben - sonst haette ein Spieler bei
        // einer zerrissenen Verbindung einen Beruf verloren UND nichts bezahlt. Diese
        // Ablage schreibt NIE, und damit wird die Gegenbuchung sichtbar.
        Kasse eigeneKasse = new SpeicherKasse(REICH);
        Berufsablage schreibtNicht = new SchreibtNicht(new SpeicherBerufe());
        Befehlsprobe mitSturerAblage = new Befehlsprobe(
                List.of(befehle(schreibtNicht, eigeneKasse, Berufswerte.VORGABE)));
        Spielerprobe cara = jeder(18);
        assertTrue(schreibtNicht.waehle(new Berufsablage.Antrag(cara.kennung(),
                "bergmann", MITTAGS), schreibtNicht.lies(cara.kennung())));

        assertEquals(1,
                mitSturerAblage.tippe("beruf verlassen bergmann bestaetigen", cara));

        assertEquals(List.of("&eDein Berufsstand hat sich eben geaendert - versuch es"
                        + " noch einmal."), cara.chatBild());
        assertEquals(REICH, eigeneKasse.guthaben(cara.kennung()),
                "die Gebuehr muss zurueck sein");
        // Und zwar als LESBARE Gegenbuchung, nicht als stilles Zurueckdrehen.
        List<Buchung> verlauf = eigeneKasse.verlauf(cara.kennung(), 10);
        assertEquals(Buchungsgrund.BERUF_ERSTATTET, verlauf.getFirst().grund(),
                verlauf.toString());
        assertEquals(Buchungsgrund.BERUF_VERLASSEN, verlauf.get(1).grund(),
                verlauf.toString());
    }

    @Test
    @DisplayName("45u - antwortet die Ablage nicht, liest der Spieler es und zahlt nicht")
    void ablageAntwortetNicht() throws CommandSyntaxException {
        Kasse eigeneKasse = new SpeicherKasse(REICH);
        Befehlsprobe mitKaputterAblage = new Befehlsprobe(
                List.of(befehle(new LiestNicht(), eigeneKasse, Berufswerte.VORGABE)));
        Spielerprobe cara = jeder(19);

        assertEquals(1, mitKaputterAblage.tippe("beruf stand", cara));
        assertEquals(1, mitKaputterAblage.tippe("beruf waehlen bergmann", cara));

        assertEquals(List.of(
                        "&7Deine Berufe sind gerade nicht lesbar - im Serverprotokoll"
                                + " steht, warum.",
                        "&7Deine Berufe sind gerade nicht lesbar - im Serverprotokoll"
                                + " steht, warum."),
                cara.chatBild(),
                "kein Wurf im Chat, sondern ein Satz - und zwar auf BEIDEN Wegen");
        assertEquals(REICH, eigeneKasse.guthaben(cara.kennung()));
    }

    @Test
    @DisplayName("45v - ohne das Grundrecht gibt es den Befehl nicht")
    void ohneGrundrecht() throws CommandSyntaxException {
        // requires und nicht eine Absage: Wer das Recht nicht hat, soll den Befehl nicht
        // in der Vervollstaendigung sehen. Das ist der Unterschied zum Recht fuer
        // Spezialisierungen - dort ist der Befehl erlaubt und die TAT nicht.
        Spielerprobe ohneRecht = spieler(20);

        assertThrows(CommandSyntaxException.class,
                () -> baum.tippe("beruf", ohneRecht));
        assertEquals(List.of(), ohneRecht.chatBild());
    }

    @Test
    @DisplayName("45w - die Vervollstaendigung schlaegt die Berufe der Datei vor")
    void vervollstaendigung() {
        // Vorgeschlagen werden die SCHLUESSEL, denn die tippt er - gelesen bekommt er die
        // Worte. Und zwar in der Reihenfolge der Datei.
        assertEquals(List.of("bergmann", "bauer", "steinmetz"), VERZEICHNIS.berufe());
        assertEquals(List.of("bergmann"), VERZEICHNIS.vorschlaege("berg"));
    }
}
