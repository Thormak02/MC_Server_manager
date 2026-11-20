package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Bedingungsmessung;
import de.freimarken.kern.Rangablage;
import de.freimarken.kern.Rangschau;
import de.freimarken.kern.Rangstand;
import de.freimarken.kern.Rangwerte;
import de.freimarken.kern.SpeicherRaenge;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /rang} GETIPPT - durch den echten Befehlsbaum.
 *
 * <p>Dieselbe Bauart wie {@link BerufBefehlTest}: Der Baum wird gebaut, der Befehl
 * getippt, und geprueft wird, was beim Spieler ANKOMMT. Keine dieser Proben ruft eine
 * Methode von {@link RangBefehle} unmittelbar auf - sonst waere genau der Weg ungeprueft,
 * auf dem ein Spieler den Befehl benutzt.
 *
 * <p><b>Was nur hier geprueft werden kann:</b> dass der Zweig ohne das Recht gar nicht da
 * ist, dass {@code /rang leiter} sein EIGENES executes hat (ohne das antwortet Brigadier
 * englisch, und der Spieler haelt den Befehl fuer kaputt), dass die Konsole eine eigene
 * Zeile bekommt und dass eine stumme Ablage keine Stufe zeigt. Die Zusammenstellung haelt
 * {@code RangschauTest} ohne Server, die Bilder halten {@link BildGleichTest} und
 * {@link SendewegTest}.
 */
@DisplayName("/rang getippt")
class RangBefehlTest {

    private static final UUID CARA = Spielerprobe.kennungAus(201);

    /** Die Leiter der Proben - drei Stufen, zwei Bedingungen auf der obersten. */
    private static final Rangwerte LEITER = new Rangwerte(true, List.of(
            new Rangwerte.Stufe("reisender", Map.of(), Map.of(), List.of()),
            new Rangwerte.Stufe("siedler", Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L),
                    Map.of(), List.of()),
            new Rangwerte.Stufe("buerger", Map.of(Bedingungsart.TAETIGE_STUNDEN, 10L,
                    Bedingungsart.PARZELLEN, 2L), Map.of(), List.of())));

    private SpeicherRaenge ablage;
    private Spielerprobe cara;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        ablage = new SpeicherRaenge();
        cara = new Spielerprobe("Cara", CARA, RangBefehle.RECHT);
    }

    private Befehlsprobe baum(Rangwerte werte, Rangablage welche, Object... messwerte) {
        Map<Bedingungsart, Long> karte = new EnumMap<>(Bedingungsart.class);
        for (int stelle = 0; stelle < messwerte.length; stelle += 2) {
            karte.put((Bedingungsart) messwerte[stelle],
                    ((Number) messwerte[stelle + 1]).longValue());
        }
        Bedingungsmessung messung = (wer, arten) -> karte;
        var befehle = new RangBefehle(new Rangschau(welche,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitRaengen(werte)),
                messung), Sprachprobe.spracheAusDemJar(),
                Logger.getLogger(RangBefehlTest.class.getName()));
        return new Befehlsprobe(List.of(befehle.rang()));
    }

    private Befehlsprobe baum(Object... messwerte) {
        return baum(LEITER, ablage, messwerte);
    }

    private void stufe(String rang) {
        ablage.setze(new Rangablage.Antrag(CARA, rang, Instant.EPOCH),
                Rangstand.leer(CARA));
    }

    @Test
    @DisplayName("46 - /rang nennt Stufe, naechste Stufe und je Bedingung ZWEI Zeilen")
    void rangNenntStandUndBedingungen() throws CommandSyntaxException {
        stufe("siedler");

        assertEquals(1, baum(Bedingungsart.TAETIGE_STUNDEN,
                12 * Bedingungsart.SEKUNDEN_JE_STUNDE,
                Bedingungsart.PARZELLEN, 1L).tippe("rang", cara));

        // DIE GANZE ANTWORT und nicht vier contains: Vertauscht jemand "verlangt" und
        // "Stand", waeren vier contains alle gruen - und der Spieler las, er habe schon
        // zwei Parzellen.
        assertEquals(List.of("&6Dein Rang",
                        "&7Du bist &fsiedler&7.",
                        "&7Als naechstes: &fbuerger&7. Dafuer zaehlt:",
                        "&7- 10 Stunden taetige Zeit",
                        "&a    du hast 12 - geschafft",
                        "&7- 2 eigene Parzellen",
                        "&7    du hast 1",
                        "&8/rang leiter zeigt alle Stufen."),
                cara.chatBild());
    }

    @Test
    @DisplayName("46b - /rang leiter nennt jede Stufe in ihrem Zustand")
    void leiterNenntJedeStufe() throws CommandSyntaxException {
        stufe("siedler");

        assertEquals(1, baum().tippe("rang leiter", cara));

        assertEquals(List.of("&6Die Rangleiter",
                        "&7  reisender - erreicht",
                        "&f  siedler - hier stehst du",
                        "&8  buerger - offen"),
                cara.chatBild());
    }

    @Test
    @DisplayName("46c - /rang leiter hat sein EIGENES executes")
    void leiterHatEigenesExecutes() throws CommandSyntaxException {
        // Ein passendes Literal verdeckt in Brigadier jedes Geschwister vollstaendig:
        // Ohne eigenes executes am Literal "leiter" waere /rang leiter ein unbekannter
        // Befehl - mit einer roten englischen Zeile, die der Spieler sich nicht erklaeren
        // kann. Belegte Lehre aus /freistadt.
        stufe("reisender");

        assertEquals(1, baum().tippe("rang", cara), "das nackte /rang");
        assertEquals(1, baum().tippe("rang leiter", cara), "und der Zweig daneben");
    }

    @Test
    @DisplayName("46d - ohne das Recht gibt es den Befehl nicht")
    void ohneRechtKeinBefehl() {
        Spielerprobe ohneRecht = new Spielerprobe("Bodo", Spielerprobe.kennungAus(202));

        assertThrows(CommandSyntaxException.class,
                () -> baum().tippe("rang", ohneRecht));
        assertTrue(ohneRecht.chatBild().isEmpty());
    }

    @Test
    @DisplayName("46e - auf der Konsole gibt es keinen Rang, und sie liest den Grund")
    void konsoleHatKeinenRang() throws CommandSyntaxException {
        Befehlsprobe baum = baum();

        assertEquals(0, baum.tippeAufDerKonsole("rang"));

        assertEquals(List.of("&7/rang zeigt den Rang eines Spielers - auf der Konsole"
                + " gibt es keinen."), baum.konsoleBild());
    }

    @Test
    @DisplayName("46f - ohne Rangleiter kommt EIN Satz und nicht nichts")
    void ohneLeiterEinSatz() throws CommandSyntaxException {
        // Ein Befehl, der nichts sagt, sieht kaputt aus - und der Spieler tippt ihn
        // wieder.
        assertEquals(1, baum(new Rangwerte(false, LEITER.leiter()), ablage)
                .tippe("rang", cara));

        assertEquals(List.of("&7Auf diesem Server gibt es keine Raenge."),
                cara.chatBild());
    }

    @Test
    @DisplayName("46g - eine stumme Ablage sagt es und zeigt KEINE Stufe")
    void stummeAblageZeigtKeineStufe() throws CommandSyntaxException {
        Rangablage stumm = new Rangablage() {
            @Override
            public Rangstand lies(UUID spieler) {
                throw new IllegalStateException("die Ablage schweigt");
            }

            @Override
            public boolean setze(Antrag antrag, Rangstand erwartet) {
                throw new IllegalStateException("die Ablage schweigt");
            }
        };

        assertEquals(0, baum(LEITER, stumm).tippe("rang", cara));

        // KEIN Rueckfall auf die unterste Stufe: Der saehe aus wie eine Auskunft und
        // waere eine Vermutung - und ein Spieler, der sich fuer einen Reisenden haelt,
        // fragt beim Team nach seinem verschwundenen Rang.
        assertEquals(1, cara.chatBild().size(), cara.chatBild().toString());
        assertTrue(cara.chatBild().get(0).contains("nicht lesen"),
                cara.chatBild().get(0));
        assertTrue(!cara.chatBild().get(0).contains("reisender"),
                "und nennt keine Stufe: " + cara.chatBild().get(0));
    }

    @Test
    @DisplayName("46h - ein Eintrag, den die Leiter nicht kennt, steht in der Antwort")
    void unbekannterEintragStehtInDerAntwort() throws CommandSyntaxException {
        stufe("stifter");

        assertEquals(1, baum().tippe("rang", cara));

        assertTrue(cara.chatBild().get(1).contains("stifter"),
                "der eingetragene Schluessel gehoert in die Zeile - sonst kann der"
                        + " Spieler nicht melden, WAS dasteht: " + cara.chatBild());
        assertTrue(cara.chatBild().get(1).contains("reisender"),
                "und daneben, was gilt: " + cara.chatBild().get(1));
    }

    @Test
    @DisplayName("46i - auf der hoechsten Stufe steht der Satz dafuer")
    void hoechsteStufeHatIhrenSatz() throws CommandSyntaxException {
        stufe("buerger");

        assertEquals(1, baum().tippe("rang", cara));

        assertEquals(List.of("&6Dein Rang",
                        "&7Du bist &fbuerger&7.",
                        "&6Damit stehst du auf der hoechsten Stufe.",
                        "&8/rang leiter zeigt alle Stufen."),
                cara.chatBild());
    }

    @Test
    @DisplayName("46j - Rangschau, Sprache und Protokoll sind Pflicht")
    void pflichtenGelten() {
        var schau = new Rangschau(ablage,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                (wer, arten) -> Map.of());
        var sprache = Sprachprobe.spracheAusDemJar();
        var protokoll = Logger.getAnonymousLogger();

        assertThrows(IllegalArgumentException.class,
                () -> new RangBefehle(null, sprache, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new RangBefehle(schau, null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new RangBefehle(schau, sprache, null));
    }
}
