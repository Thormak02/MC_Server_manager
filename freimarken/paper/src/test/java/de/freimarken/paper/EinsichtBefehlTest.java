package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Einsichtsstand;
import de.freimarken.kern.Einsichtswerte;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /einsehen} - durch den echten Befehlsbaum (Schritt 16a Teil D).
 *
 * <p><b>Die tragende Probe ist die zweite</b>: Jedes Einschalten geht ins Protokoll.
 * Einsicht ist ein Zugriff auf fremdes Eigentum, und ohne diese Zeile gibt es keine
 * Spur davon, dass jemand in eine Kiste gesehen hat - genau die braucht man, wenn ein
 * Spieler fragt, wer in seiner Truhe war.
 *
 * <p>Die Uhr ist eine NAHT, und die Zone kommt von aussen: Beide Zeitangaben dieses
 * Befehls waeren sonst nur in einem Fenster am Tag richtig.
 */
@DisplayName("Der Befehl /einsehen")
class EinsichtBefehlTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    /** Dieselbe Zone wie bei den Strafen - 12:00 UTC ist dort 13:00. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final List<LogRecord> protokollzeilen = new ArrayList<>();

    /** Die Uhr dieser Probe - veraenderbar, denn das Verlaengern braucht zwei Zeiten. */
    private final Instant[] jetzt = {MITTAGS};

    private Einsichtsstand stand;
    private Sprache sprache;
    private Logger protokoll;
    private Spielerprobe cara;

    @BeforeAll
    static void server() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void frisch() {
        stand = new Einsichtsstand(() -> Einsichtswerte.VORGABE);
        sprache = Sprachprobe.spracheAusDemJar();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        jetzt[0] = MITTAGS;
        cara = new Spielerprobe("Cara", Spielerprobe.kennungAus(881),
                EinsichtBefehle.RECHT);
    }

    @Test
    @DisplayName("1 - /einsehen an schaltet ein und nennt Zeitpunkt UND Restdauer")
    void anSchaltetEin() throws CommandSyntaxException {
        probe().tippe("einsehen an", cara);

        assertEquals(1, cara.chatBild().size(), cara.chatBild().toString());
        String zeile = cara.chatBild().get(0);
        // BEIDES: Den Zeitpunkt merkt man sich, die Restdauer sagt, ob es noch reicht.
        assertTrue(zeile.contains("13:05:00"), "der Zeitpunkt fehlt: " + zeile);
        assertTrue(zeile.contains("5 Min."), "die Restdauer fehlt: " + zeile);
        assertTrue(stand.schaut(cara.spieler().getUniqueId(), MITTAGS),
                "und der Stand steht auch wirklich an");
    }

    @Test
    @DisplayName("2 - JEDES Einschalten geht ins PROTOKOLL, mit Namen")
    void einschaltenGehtInsProtokoll() throws CommandSyntaxException {
        // DIE PROBE DIESES BEFEHLS. Plan 12c.3: "alles protokolliert, mit Grund und
        // Bearbeiter". Ohne diese Zeile ist die Einsicht ein Zugriff ohne Spur.
        probe().tippe("einsehen an", cara);

        assertEquals(1, protokollzeilen.size(), "genau eine Zeile");
        String zeile = protokollzeilen.get(0).getMessage();
        assertTrue(zeile.contains("Cara"), "WER: " + zeile);
        assertTrue(zeile.contains("sieht ein"), "WAS: " + zeile);
    }

    @Test
    @DisplayName("3 - ein zweites /einsehen an VERLAENGERT - und protokolliert erneut")
    void zweitesAnVerlaengert() throws CommandSyntaxException {
        Befehlsprobe baum = probe();
        baum.tippe("einsehen an", cara);

        jetzt[0] = MITTAGS.plusSeconds(180L);
        baum.tippe("einsehen an", cara);

        // Der Weg fuer eine Untersuchung, die laenger dauert als die Frist: ein
        // Tastendruck und eine Zeile im Protokoll.
        assertTrue(cara.chatBild().get(1).contains("13:08:00"),
                "die neue Frist: " + cara.chatBild());
        assertTrue(stand.schaut(cara.spieler().getUniqueId(),
                        MITTAGS.plusSeconds(400L)),
                "die alte Frist waere hier abgelaufen");
        assertEquals(2, protokollzeilen.size(),
                "auch das Verlaengern ist ein Zugriff - und steht im Protokoll");
    }

    @Test
    @DisplayName("4 - /einsehen aus schaltet aus und protokolliert es")
    void ausSchaltetAus() throws CommandSyntaxException {
        Befehlsprobe baum = probe();
        baum.tippe("einsehen an", cara);

        baum.tippe("einsehen aus", cara);

        assertTrue(cara.chatBild().get(1).contains("nicht mehr ein"),
                cara.chatBild().toString());
        assertFalse(stand.schaut(cara.spieler().getUniqueId(), MITTAGS));
        assertEquals(2, protokollzeilen.size(),
                "das Ende des Zugriffs gehoert genauso ins Protokoll wie sein Beginn");
    }

    @Test
    @DisplayName("5 - /einsehen aus OHNE Einsicht sagt es - und protokolliert nichts")
    void ausOhneEinsicht() throws CommandSyntaxException {
        // Ein "ist jetzt aus" auf etwas, das nie an war, laesst einen Moderator
        // glauben, er habe eben noch eingesehen - und das ist die Annahme, mit der man
        // eine fremde Kiste fuer die eigene haelt.
        probe().tippe("einsehen aus", cara);

        assertTrue(cara.chatBild().get(0).contains("gar nicht eingesehen"),
                cara.chatBild().toString());
        // Und keine Zeile: Ein Protokoll, in dem auch das Nichtstun steht, ist eines,
        // in dem man den Zugriff nicht mehr findet.
        assertTrue(protokollzeilen.isEmpty(), "nichts geschehen, nichts zu protokollieren");
    }

    @Test
    @DisplayName("6 - der NACKTE Befehl zeigt den Stand AN - mit Zeitpunkt")
    void nackterBefehlZeigtStandAn() throws CommandSyntaxException {
        // Der nackte Befehl braucht sein EIGENES executes: Ohne es endet /einsehen in
        // Brigadiers englischem "Unknown or incomplete command" - und er ist der
        // haeufigste, denn er beantwortet die Frage "sehe ich noch ein?".
        Befehlsprobe baum = probe();
        baum.tippe("einsehen an", cara);

        baum.tippe("einsehen", cara);

        assertTrue(cara.chatBild().get(1).contains("Du siehst ein bis 13:05:00"),
                cara.chatBild().toString());
    }

    @Test
    @DisplayName("7 - der nackte Befehl zeigt den Stand AUS")
    void nackterBefehlZeigtStandAus() throws CommandSyntaxException {
        probe().tippe("einsehen", cara);

        assertEquals(1, cara.chatBild().size(), cara.chatBild().toString());
        assertTrue(cara.chatBild().get(0).contains("Du siehst nicht ein"),
                cara.chatBild().toString());
    }

    @Test
    @DisplayName("8 - NACH der Frist sagt der Stand aus - ohne Zutun")
    void nachDerFristStandAus() throws CommandSyntaxException {
        Befehlsprobe baum = probe();
        baum.tippe("einsehen an", cara);

        jetzt[0] = MITTAGS.plusSeconds(300L);
        baum.tippe("einsehen", cara);

        // Die Frist ist ein ZEITPUNKT und kein Zaehler: Ein Zaehler braeuchte einen
        // Takt, der ihn herunterzaehlt, und ein Takt, der ausfaellt, liesse die
        // Einsicht offen.
        assertTrue(cara.chatBild().get(1).contains("Du siehst nicht ein"),
                cara.chatBild().toString());
    }

    @Test
    @DisplayName("9 - OHNE das Recht gibt es den Befehl nicht")
    void ohneRechtKeinBefehl() {
        Spielerprobe bert = new Spielerprobe("Bert", Spielerprobe.kennungAus(882));

        // Brigadier versteckt den Zweig, statt ihn abzulehnen - fuer den Spieler sieht
        // das aus wie ein Befehl, den es nicht gibt. Das ist hier richtig: Wer nicht
        // einsehen darf, muss nicht erfahren, dass es das gibt.
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("einsehen an", bert));
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("einsehen", bert));
        assertTrue(bert.chatBild().isEmpty());
    }

    @Test
    @DisplayName("10 - die KONSOLE klickt nicht in Kisten - alle drei Zweige sagen es")
    void konsoleWirdAbgewiesen() throws CommandSyntaxException {
        Befehlsprobe baum = probe();

        baum.tippeAufDerKonsole("einsehen");
        baum.tippeAufDerKonsole("einsehen an");
        baum.tippeAufDerKonsole("einsehen aus");

        assertEquals(3, baum.konsoleBild().size(), baum.konsoleBild().toString());
        for (String zeile : baum.konsoleBild()) {
            assertTrue(zeile.contains("Einsehen kann ein Spieler"), zeile);
        }
        // Und der Riegel wirkt WIRKLICH: Eine Antwort ohne Abbruch waere eine Einsicht
        // fuer eine Kennung, die niemandem gehoert.
        assertEquals(0, stand.anzahl(MITTAGS), "nichts eingeschaltet");
        assertTrue(protokollzeilen.isEmpty(), "und nichts protokolliert");
    }

    @Test
    @DisplayName("11 - beide Zweige haengen am Baum - das Vorschlagsfenster zeigt sie")
    void zweigeHaengenAmBaum() {
        assertEquals(List.of("an", "aus"), probe().vorschlaege("einsehen ", cara));
    }

    @Test
    @DisplayName("12 - ohne Bauteile gibt es den Befehl nicht")
    void fehlendeBauteileWerfen() {
        assertThrows(IllegalArgumentException.class, () -> new EinsichtBefehle(
                null, sprache, () -> MITTAGS, () -> ZONE, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new EinsichtBefehle(
                stand, null, () -> MITTAGS, () -> ZONE, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new EinsichtBefehle(
                stand, sprache, null, () -> ZONE, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new EinsichtBefehle(
                stand, sprache, () -> MITTAGS, null, protokoll));
        // Ohne das Protokoll waere der Befehl ein Zugriff ohne Spur - und das ist die
        // eine Zusage, die dieser Befehl macht.
        assertThrows(IllegalArgumentException.class, () -> new EinsichtBefehle(
                stand, sprache, () -> MITTAGS, () -> ZONE, null));
    }

    // ---------------------------------------------------------- Werkzeug

    private Befehlsprobe probe() {
        EinsichtBefehle befehle = new EinsichtBefehle(stand, sprache, () -> jetzt[0],
                () -> ZONE, protokoll);
        return new Befehlsprobe(List.of(befehle.einsehen()));
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("EinsichtprobeLog-" + UUID.randomUUID());
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
}
