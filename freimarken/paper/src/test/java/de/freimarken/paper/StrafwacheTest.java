package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.SpeicherStrafen;
import de.freimarken.kern.Strafablage;
import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafbuch;
import de.freimarken.kern.Strafdauer;
import de.freimarken.kern.Strafe;
import de.freimarken.kern.Strafwerte;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Wache, die Strafen WIRKSAM macht - Tuer, Chat und gesperrte Befehle.
 *
 * <p><b>Ohne diese Wache ist der ganze Teil B unsichtbar wirkungslos:</b> Der Kern
 * prueft die Rechte, die Ablage haelt die Strafe, {@code /bannliste} zeigt sie - und ein
 * gebannter Spieler kommt herein, ein stummgeschalteter schreibt weiter.
 *
 * <p><b>Geprueft werden die drei ENTSCHEIDUNGEN, nicht die Ereigniszweige</b>, und das
 * ist eine Messung: {@code AsyncPlayerPreLoginEvent} und
 * {@code PlayerCommandPreprocessEvent} lassen sich ohne laufenden Server nicht bauen -
 * sie brauchen {@code Server.createProfile} beziehungsweise {@code Player.getServer}
 * (gemessen am 25.11.2026). Darum stehen die Entscheidungen in eigenen Methoden, und
 * {@code GrenzeTest} haelt, dass die Ereigniszweige sie wirklich rufen.
 *
 * <p>Die wichtigste Probe ist {@link #gestoerteAblageLaesstDurch()}: Sie nagelt die
 * Richtung fest, in der diese Wache bei einer Datenbankstoerung irrt.
 */
@DisplayName("Die Strafwache")
class StrafwacheTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private static final UUID BERT = Spielerprobe.kennungAus(871);

    private SpeicherStrafen ablage;
    private Sprache sprache;

    @BeforeAll
    static void server() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void frisch() {
        ablage = new SpeicherStrafen();
        sprache = Sprachprobe.spracheAusDemJar();
    }

    // -------------------------------------------------------------- die Tuer

    @Test
    @DisplayName("1 - DIE TUER: ein gebannter Spieler bekommt eine Zeile mit GRUND")
    void gebannterWirdAbgewiesen() {
        ablage.lege(BERT, Strafart.BANN, "Grief in Altfurt", null, MITTAGS,
                new Strafdauer(Duration.ofHours(24L)));

        Optional<Component> zeile = wache().abweisungFuer(BERT, "Bert");

        assertTrue(zeile.isPresent(), "er kommt nicht herein");
        String bild = Sprachprobe.bild(zeile.get());
        // Plan 12c.3: "der Grund wird dem Betroffenen angezeigt. Eine Strafe, die
        // niemand erklaert, erzeugt nur Wiederkehr."
        assertTrue(bild.contains("Grief in Altfurt"), bild);
    }

    @Test
    @DisplayName("2 - wer nicht gebannt ist, kommt herein")
    void ungestrafterKommtHerein() {
        assertTrue(wache().abweisungFuer(BERT, "Bert").isEmpty());
    }

    @Test
    @DisplayName("3 - eine STUMMSCHALTUNG sperrt die Tuer NICHT")
    void stummSperrtDieTuerNicht() {
        ablage.lege(BERT, Strafart.STUMM, "Beleidigung", null, MITTAGS,
                new Strafdauer(Duration.ofHours(1L)));

        // Zwei Leute im Streit trennt man, ohne sie vom Server zu nehmen. Eine Wache,
        // die hier abwiese, machte aus jeder Stummschaltung einen Bann.
        assertTrue(wache().abweisungFuer(BERT, "Bert").isEmpty());
    }

    @Test
    @DisplayName("4 - ein abgelaufener Bann laesst herein")
    void abgelaufenerBannLaesstHerein() {
        ablage.lege(BERT, Strafart.BANN, "Grief", null,
                MITTAGS.minusSeconds(7200L), new Strafdauer(Duration.ofHours(1L)));

        assertTrue(wache().abweisungFuer(BERT, "Bert").isEmpty(),
                "abgesessen ist abgesessen");
    }

    @Test
    @DisplayName("5 - ein AUFGEHOBENER Bann laesst herein")
    void aufgehobenerBannLaesstHerein() {
        Strafe eine = ablage.lege(BERT, Strafart.BANN, "Grief", null, MITTAGS,
                new Strafdauer(Duration.ofHours(24L)));
        ablage.hebeAuf(eine.aufgehoben(null, "Einspruch angenommen",
                MITTAGS.plusSeconds(60L)));

        // Ohne diese Richtung wirkt eine aufgehobene Sperre weiter - und das ist der
        // Fehler, den ein Zwischenspeicher machen wuerde.
        assertTrue(wache().abweisungFuer(BERT, "Bert").isEmpty());
    }

    @Test
    @DisplayName("6 - ein DAUERHAFTER Bann nennt kein Datum")
    void dauerhafterBannNenntKeinDatum() {
        ablage.lege(BERT, Strafart.BANN, "Wiederholungstaeter", null, MITTAGS,
                Strafdauer.UNBEGRENZT);

        String bild = Sprachprobe.bild(
                wache().abweisungFuer(BERT, "Bert").orElseThrow());

        // Ein Datum im Jahr 2126 liest sich wie eine Frist, und ein Spieler, der
        // darauf wartet, ist einer, dem niemand gesagt hat, dass er nicht mehr kommt.
        assertFalse(bild.contains("2126"), bild);
        assertTrue(bild.contains("dauerhaft"), bild);
        assertTrue(bild.contains("Wiederholungstaeter"), bild);
    }

    // -------------------------------------------------------------- der Chat

    @Test
    @DisplayName("7 - DER CHAT: ein stummgeschalteter Spieler bekommt Grund und Rest")
    void stummgeschalteterWirdAbgewiesen() {
        ablage.lege(BERT, Strafart.STUMM, "Beleidigung", null, MITTAGS,
                new Strafdauer(Duration.ofMinutes(10L)));

        List<Component> absage = wache().absageFuerChat(BERT, "Bert");

        assertFalse(absage.isEmpty(), "er schreibt nicht");
        String bild = String.join(" ",
                absage.stream().map(Sprachprobe::bild).toList());
        assertTrue(bild.contains("Beleidigung"), bild);
    }

    @Test
    @DisplayName("8 - wer nicht stumm ist, schreibt")
    void ungestrafterSchreibt() {
        assertTrue(wache().absageFuerChat(BERT, "Bert").isEmpty());
    }

    @Test
    @DisplayName("9 - ein BANN sperrt den Chat nicht eigens")
    void bannSperrtDenChatNicht() {
        ablage.lege(BERT, Strafart.BANN, "Grief", null, MITTAGS,
                new Strafdauer(Duration.ofHours(1L)));

        // Wer nicht hereinkommt, schreibt nicht - ein zweiter Riegel waere einer,
        // der sich nicht pruefen laesst. Und wer gebannt ist und doch im Spiel steht
        // (die Sperre kam, waehrend er drin war), soll nicht AUCH stumm sein, denn
        // dann kann er nicht einmal nachfragen.
        assertTrue(wache().absageFuerChat(BERT, "Bert").isEmpty());
    }

    // ---------------------------------------------------------- die Befehle

    @Test
    @DisplayName("10 - DIE LUECKE: /msg ist gesperrt, solange er stumm ist")
    void gesperrterBefehlWirdAbgewiesen() {
        ablage.lege(BERT, Strafart.STUMM, "Beleidigung", null, MITTAGS,
                new Strafdauer(Duration.ofMinutes(10L)));

        Optional<Component> absage = wache().absageFuerBefehl(BERT, "Bert",
                "/msg Hans du Depp");

        // OHNE DIESEN ZWEIG ist jede Stummschaltung umgehbar, und der Moderator haelt
        // den Spieler fuer still.
        assertTrue(absage.isPresent());
        String bild = Sprachprobe.bild(absage.get());
        assertTrue(bild.contains("msg"), bild);
        // Die ZEILE des Spielers steht NICHT darin: In ihr steht, was er schreiben
        // wollte, und das gehoert nicht in seine eigene Absage zurueck.
        assertFalse(bild.contains("Depp"), bild);
    }

    @Test
    @DisplayName("11 - der NAMENSRAUM wird mitgefangen")
    void namensraumWirdGefangen() {
        ablage.lege(BERT, Strafart.STUMM, "Beleidigung", null, MITTAGS,
                new Strafdauer(Duration.ofMinutes(10L)));

        // Paper macht jeden Befehl auch unter plugin:befehl erreichbar - ohne diesen
        // Fang waere /minecraft:msg der Weg um jede Stummschaltung herum.
        assertTrue(wache().absageFuerBefehl(BERT, "Bert", "/minecraft:msg Hans")
                .isPresent());
    }

    @Test
    @DisplayName("12 - /melden bleibt offen, auch wenn er stumm ist")
    void meldenBleibtOffen() {
        ablage.lege(BERT, Strafart.STUMM, "Beleidigung", null, MITTAGS,
                new Strafdauer(Duration.ofMinutes(10L)));

        // Wer gerade bestraft wurde, ist nicht derjenige, dem man den Meldeweg nimmt -
        // gerade er braeuchte ihn gegen einen Moderator.
        assertTrue(wache().absageFuerBefehl(BERT, "Bert", "/melden Chef zu hart")
                .isEmpty());
        assertTrue(wache().absageFuerBefehl(BERT, "Bert", "/heim").isEmpty());
    }

    @Test
    @DisplayName("13 - ein gesperrter Befehl von einem NICHT Stummen geht durch")
    void gesperrterBefehlOhneStrafeGehtDurch() {
        assertTrue(wache().absageFuerBefehl(BERT, "Bert", "/msg Hans hallo").isEmpty());
    }

    @Test
    @DisplayName("14 - die Liste aus der Datei entscheidet, nicht das Programm")
    void listeAusDerDateiEntscheidet() {
        ablage.lege(BERT, Strafart.STUMM, "Beleidigung", null, MITTAGS,
                new Strafdauer(Duration.ofMinutes(10L)));
        Strafwache ohneListe = wache(Strafwerte.VORGABE.mitStummSperrtBefehle(
                List.of()), ablage);

        // Eine leere Liste ist ein klarer Wunsch des Betreibers - sie gilt. Was sie
        // bedeutet, sagt der Startbericht.
        assertTrue(ohneListe.absageFuerBefehl(BERT, "Bert", "/msg Hans").isEmpty());

        Strafwache eigeneListe = wache(Strafwerte.VORGABE.mitStummSperrtBefehle(
                List.of("brief")), ablage);

        assertTrue(eigeneListe.absageFuerBefehl(BERT, "Bert", "/brief Hans")
                .isPresent(), "ein Befehl aus einem Nachbarplugin");
        assertTrue(eigeneListe.absageFuerBefehl(BERT, "Bert", "/msg Hans").isEmpty(),
                "und /msg ist dann frei - die Datei entscheidet");
    }

    // ---------------------------------------------------------- die Stoerung

    @Test
    @DisplayName("15 - DIE RICHTUNG: eine gestoerte Ablage laesst durch, in ALLEN drei"
            + " Zweigen")
    void gestoerteAblageLaesstDurch() {
        Strafwache gestoert = wache(Strafwerte.VORGABE, new WerfendeAblage());

        // DIE ENTSCHEIDUNG DIESER KLASSE, und sie ist eine Betriebsentscheidung: Die
        // Gegenrichtung waere ein Server, der bei einer Datenbankstoerung fuer ALLE zu
        // ist - aus einem Riegel gegen einen Griefer wuerde ein Riegel gegen jeden.
        // Jeder solche Fall geht als Warnung ins Protokoll.
        assertTrue(gestoert.abweisungFuer(BERT, "Bert").isEmpty(),
                "die Tuer bleibt offen");
        assertTrue(gestoert.absageFuerChat(BERT, "Bert").isEmpty(),
                "er darf sprechen");
        assertTrue(gestoert.absageFuerBefehl(BERT, "Bert", "/msg Hans").isEmpty(),
                "und seinen Fluesterbefehl benutzen");
    }

    @Test
    @DisplayName("16 - ohne Bauteile gibt es keine Wache")
    void fehlendeBauteileWerfen() {
        Strafbuch buch = new Strafbuch(ablage, () -> Strafwerte.VORGABE,
                StrafBefehle.RECHTE, wer -> false);
        Logger stumm = Logger.getLogger("StrafwacheProbe-" + UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> new Strafwache(null, sprache,
                () -> ZONE, StrafwacheTest::nameVon, () -> MITTAGS, stumm));
        assertThrows(IllegalArgumentException.class, () -> new Strafwache(buch, null,
                () -> ZONE, StrafwacheTest::nameVon, () -> MITTAGS, stumm));
        assertThrows(IllegalArgumentException.class, () -> new Strafwache(buch, sprache,
                null, StrafwacheTest::nameVon, () -> MITTAGS, stumm));
        assertThrows(IllegalArgumentException.class, () -> new Strafwache(buch, sprache,
                () -> ZONE, null, () -> MITTAGS, stumm));
        assertThrows(IllegalArgumentException.class, () -> new Strafwache(buch, sprache,
                () -> ZONE, StrafwacheTest::nameVon, () -> MITTAGS, null));
        // Ohne die Uhr haengt jede Frage an der Tageszeit - und eine Probe, die von
        // ihr abhaengt, ist keine.
        assertThrows(IllegalArgumentException.class, () -> new Strafwache(buch, sprache,
                () -> ZONE, StrafwacheTest::nameVon, null, stumm));
    }

    @Test
    @DisplayName("17 - die KONSOLE als Absender hat ein Wort in der Zeile")
    void konsoleHatEinWort() {
        ablage.lege(BERT, Strafart.RAUSWURF, "Spam", null, MITTAGS, null);
        ablage.lege(BERT, Strafart.BANN, "Grief", null, MITTAGS,
                new Strafdauer(Duration.ofHours(1L)));

        String bild = Sprachprobe.bild(
                wache().abweisungFuer(BERT, "Bert").orElseThrow());

        // Ohne das Wort stuende in der Zeile eine leere Stelle, und ein erfundener
        // Name waere schlimmer: Er saehe wie ein Spieler aus.
        assertFalse(bild.contains("null"), bild);
    }

    // ---------------------------------------------------------- Werkzeug

    private Strafwache wache() {
        return wache(Strafwerte.VORGABE, ablage);
    }

    private Strafwache wache(Strafwerte werte, Strafablage welche) {
        // DIE UHR ALS NAHT: Vorher rief die Wache Instant#now, und diese Proben legen
        // ihre Strafen auf MITTAGS mit kurzer Dauer - sie waren damit nur in einem
        // Fenster von zehn Minuten am Tag gruen. Gefunden am 25.11.2026 dadurch, dass
        // derselbe Lauf zwei Stunden spaeter rot war.
        return new Strafwache(new Strafbuch(welche, () -> werte,
                StrafBefehle.RECHTE, wer -> false), sprache, () -> ZONE,
                StrafwacheTest::nameVon, () -> MITTAGS,
                Logger.getLogger("StrafwacheProbe-" + UUID.randomUUID()));
    }

    private static String nameVon(UUID wer) {
        return BERT.equals(wer) ? "Bert" : "?" + wer;
    }

    /** Eine Ablage, die bei jedem Zugriff wirft - fuer die Richtung der Stoerung. */
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
