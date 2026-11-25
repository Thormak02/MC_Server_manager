package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.SpeicherStrafen;
import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafbuch;
import de.freimarken.kern.Strafdauer;
import de.freimarken.kern.Strafe;
import de.freimarken.kern.Strafwerte;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /strafe} und {@code /bannliste} GETIPPT.
 *
 * <p><b>Diese Datei traegt eine Deckung, die anderswo zugesagt ist:</b> Sowohl
 * {@code Sprachschluessel.familienDeckung} als auch {@code SendewegTest} 33x erklaeren
 * die drei Artworte fuer selbstbescheinigend und verweisen auf <i>diese</i> Probe -
 * <b>jede</b> Art muss hier durch die echte Zeile aus der ausgelieferten Vorlage gehen.
 * Ohne {@link #jedeArtHatIhrWort()} waere das eine Zusage ohne Bau, und eine Art ohne
 * Wort stuende im Spiel mit ihrem Pfad da.
 *
 * <p><b>Die zweite tragende Probe ist {@link #bannWirftHinaus()}:</b> Ohne das
 * Hinauswerfen wirkt ein Bann erst beim naechsten Beitritt - der Moderator liest
 * "gesperrt bis morgen", und der Griefer baut weiter ab.
 */
@DisplayName("/strafe getippt")
class StrafBefehlTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private static final UUID BERT = Spielerprobe.kennungAus(851);

    private static final UUID CLARA = Spielerprobe.kennungAus(852);

    private SpeicherStrafen ablage;
    private Sprache sprache;
    private Spielerprobe moderator;
    private Spielerprobe verwalter;

    /** Wen die Teamfrage als Teammitglied meldet. */
    private final List<UUID> imTeam = new ArrayList<>();

    /** Wer hinausgeworfen wurde, samt Zeile. */
    private final List<String> hinausgeworfen = new ArrayList<>();

    /** Was ein anwesender Betroffener gelesen hat. */
    private final List<String> gesagt = new ArrayList<>();

    /** Was der Ansagekreis gelesen hat. */
    private final List<String> angesagt = new ArrayList<>();

    /** Wer gerade angemeldet ist - fuer Spielerhand und Ansagekreis. */
    private final List<UUID> anwesend = new ArrayList<>();

    @BeforeAll
    static void server() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void frisch() {
        ablage = new SpeicherStrafen();
        sprache = Sprachprobe.spracheAusDemJar();
        moderator = new Spielerprobe("Chef", Spielerprobe.kennungAus(853),
                StrafBefehle.RECHTE.stumm(), StrafBefehle.RECHTE.rauswurf(),
                StrafBefehle.RECHTE.bann(), StrafBefehle.RECHTE.aufheben(),
                StrafBefehle.RECHT_BANNLISTE);
        verwalter = new Spielerprobe("Rat", Spielerprobe.kennungAus(854),
                StrafBefehle.RECHTE.stumm(), StrafBefehle.RECHTE.rauswurf(),
                StrafBefehle.RECHTE.bann(), StrafBefehle.RECHTE.aufheben(),
                StrafBefehle.RECHTE.dauerhaft(), StrafBefehle.RECHTE.team(),
                StrafBefehle.RECHT_BANNLISTE);
        imTeam.clear();
        hinausgeworfen.clear();
        gesagt.clear();
        angesagt.clear();
        anwesend.clear();
    }

    // ------------------------------------------------------------- verhaengen

    @Test
    @DisplayName("1 - /strafe bann nennt Nummer, Dauer und Grund")
    void bannVerhaengt() throws CommandSyntaxException {
        List<String> zeilen = tippe("strafe bann Bert 1h Grief in Altfurt");

        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("Bert"), zeilen.get(0));
        assertTrue(zeilen.get(0).contains("Grief in Altfurt"), zeilen.get(0));
        assertTrue(zeilen.get(0).contains("1 Std."), zeilen.get(0));
        Strafe eine = ablage.geltende(BERT, Strafart.BANN, MITTAGS).orElseThrow();
        assertEquals("Grief in Altfurt", eine.grund());
        assertEquals(Optional.of(MITTAGS.plusSeconds(3600L)), eine.endet());
    }

    @Test
    @DisplayName("2 - DER GRUND ist Pflicht: /strafe bann ohne Grund tut nichts")
    void grundIstPflicht() throws CommandSyntaxException {
        // Brigadier weist den Zweig ohne Grund selbst ab - er hat kein executes
        // ohne das Argument. Statt einer englischen Zeile kommt der HINWEIS, und
        // verhaengt ist nichts: Plan 12c.3 traegt darauf, dass eine Strafe erklaert
        // wird.
        List<String> zeilen = tippe("strafe bann Bert 1h");

        assertFalse(zeilen.isEmpty());
        assertTrue(ablage.geltende(BERT, Strafart.BANN, MITTAGS).isEmpty(),
                "ohne Grund wird nichts verhaengt");
    }

    @Test
    @DisplayName("3 - /strafe allein nennt die Zweige statt einer englischen Zeile")
    void hinweisStattEnglisch() throws CommandSyntaxException {
        List<String> zeilen = tippe("strafe");

        assertFalse(zeilen.isEmpty(), "ohne Hinweis endet /strafe in Brigadiers"
                + " englischem \"Unknown or incomplete command\"");
        String ganz = String.join(" ", zeilen);
        for (Strafart art : Strafart.values()) {
            assertTrue(ganz.contains(art.kuerzel()),
                    art + " fehlt im Hinweis: " + ganz);
        }
    }

    @Test
    @DisplayName("4 - ein unbekannter Spieler wird genannt und nicht gestraft")
    void unbekannterSpieler() throws CommandSyntaxException {
        List<String> zeilen = tippe("strafe bann Zora 1h Grief");

        assertTrue(zeilen.get(0).contains("Zora"), zeilen.get(0));
        assertEquals(0, ablage.zaehle(BERT, Strafart.BANN));
    }

    @Test
    @DisplayName("5 - eine unbrauchbare Dauer nennt BEISPIELE")
    void unbrauchbareDauer() throws CommandSyntaxException {
        // Die Beispiele sind der Zweck des Satzes: Wer 10 getippt hat, braucht 10m -
        // und soll das lesen und nicht erraten.
        List<String> zeilen = tippe("strafe bann Bert 10 Grief");

        assertTrue(zeilen.get(0).contains("10m"),
                "die Absage nennt die brauchbaren Formen: " + zeilen.get(0));
        assertEquals(0, ablage.zaehle(BERT, Strafart.BANN));
    }

    @Test
    @DisplayName("6 - DER RIEGEL: dauerhaft braucht sein Recht, und die Absage nennt"
            + " die Grenze")
    void dauerhaftBrauchtSeinRecht() throws CommandSyntaxException {
        List<String> zeilen = tippe("strafe bann Bert dauerhaft Wiederholungstaeter");

        assertTrue(zeilen.get(0).contains("30 Tg."),
                "die Absage NENNT die Grenze - wer nur \"zu lang\" liest, tippt als"
                        + " naechstes dieselbe Zahl: " + zeilen.get(0));
        assertEquals(0, ablage.zaehle(BERT, Strafart.BANN));

        List<String> alsVerwalter = tippe(
                "strafe bann Bert dauerhaft Wiederholungstaeter", verwalter);

        assertTrue(ablage.geltende(BERT, Strafart.BANN, MITTAGS).orElseThrow()
                        .unbegrenzt(), alsVerwalter.toString());
    }

    @Test
    @DisplayName("7 - DER ZWEITE FAENGER: eine zu LANGE Dauer braucht dasselbe Recht")
    void zuLangeBrauchtDasselbeRecht() throws CommandSyntaxException {
        // Ohne diesen zweiten Faenger tippt ein Moderator 3650d: formal befristet und
        // praktisch endgueltig.
        List<String> zeilen = tippe("strafe bann Bert 3650d Wiederholungstaeter");

        assertTrue(zeilen.get(0).contains("30 Tg."), zeilen.get(0));
        assertEquals(0, ablage.zaehle(BERT, Strafart.BANN));
    }

    @Test
    @DisplayName("8 - GENAU an der Grenze geht es durch")
    void genauAnDerGrenze() throws CommandSyntaxException {
        // Der dritte Zustand mit eigenem Satz: Ohne diese Probe waeren > und >=
        // gleich, solange keine Zahl die Gleichheit erreicht.
        tippe("strafe bann Bert 720h Grief in Altfurt");

        assertTrue(ablage.geltende(BERT, Strafart.BANN, MITTAGS).isPresent(),
                "720 Stunden sind die Hoechstdauer und damit erlaubt");
    }

    @Test
    @DisplayName("9 - DER TEAMRIEGEL: ein Teammitglied braucht das Teamrecht")
    void teamBrauchtDasTeamrecht() throws CommandSyntaxException {
        imTeam.add(BERT);

        List<String> zeilen = tippe("strafe bann Bert 1h Streit");

        assertTrue(zeilen.get(0).contains("Bert"), zeilen.get(0));
        assertEquals(0, ablage.zaehle(BERT, Strafart.BANN),
                "ohne das Teamrecht wird ein Kollege nicht gebannt");

        tippe("strafe bann Bert 1h Streit", verwalter);

        assertEquals(1, ablage.zaehle(BERT, Strafart.BANN),
                "ein Verwalter darf es");
    }

    @Test
    @DisplayName("10 - eine gestoerte Teamfrage schuetzt den Betroffenen")
    void gestoerteTeamfrageSchuetzt() throws CommandSyntaxException {
        // Von zwei Fehlern der, der sich zuruecknehmen laesst: Wer nicht weiss, ob der
        // Betroffene im Team ist, soll nicht strafen.
        List<String> zeilen = tippe("strafe bann Bert 1h Streit", moderator, true);

        assertTrue(zeilen.get(0).contains("Bert"), zeilen.get(0));
        assertEquals(0, ablage.zaehle(BERT, Strafart.BANN));
    }

    // ------------------------------------------------------------- Wirkung

    @Test
    @DisplayName("11 - DIE WIRKUNG: ein Bann wirft den anwesenden Spieler hinaus")
    void bannWirftHinaus() throws CommandSyntaxException {
        anwesend.add(BERT);

        tippe("strafe bann Bert 1h Grief in Altfurt");

        // OHNE DIESE ZEILE wirkt ein Bann erst beim naechsten Beitritt: Der Moderator
        // liest "gesperrt bis morgen", und der Griefer baut weiter ab.
        assertEquals(1, hinausgeworfen.size(), hinausgeworfen.toString());
        assertTrue(hinausgeworfen.get(0).contains("Grief in Altfurt"),
                "die Zeile an der Tuer nennt den GRUND: " + hinausgeworfen.get(0));
        assertTrue(gesagt.isEmpty(), "gesprochen wird nicht - er ist weg");
    }

    @Test
    @DisplayName("12 - ein Rauswurf wirft hinaus und steht trotzdem im Protokoll")
    void rauswurfWirftHinaus() throws CommandSyntaxException {
        anwesend.add(BERT);

        tippe("strafe rauswurf Bert Spam im Chat");

        assertEquals(1, hinausgeworfen.size(), hinausgeworfen.toString());
        assertTrue(hinausgeworfen.get(0).contains("Spam im Chat"),
                hinausgeworfen.get(0));
        // Die Zeile, die zeigt, dass derselbe Spieler vor dem Bann dreimal
        // rausgeworfen wurde, ist der eigentliche Fund.
        assertEquals(1, ablage.zaehle(BERT, Strafart.RAUSWURF));
    }

    @Test
    @DisplayName("13 - eine Stummschaltung wirft NICHT hinaus, sondern spricht")
    void stummSprichtNur() throws CommandSyntaxException {
        anwesend.add(BERT);

        tippe("strafe stumm Bert 10m Beleidigung");

        assertTrue(hinausgeworfen.isEmpty(),
                "zwei Leute im Streit trennt man, ohne sie vom Server zu nehmen");
        assertFalse(gesagt.isEmpty(), "er erfaehrt es");
        assertTrue(String.join(" ", gesagt).contains("Beleidigung"),
                gesagt.toString());
    }

    @Test
    @DisplayName("14 - ein ABWESENDER wird gestraft, ohne dass etwas geschieht")
    void abwesenderWirdGestraft() throws CommandSyntaxException {
        // Ein Griefer verschwindet, bevor man ihn sperrt. Die Zeile liest er an der
        // Tuer, und die Wache hat sie.
        tippe("strafe bann Bert 1h Grief in Altfurt");

        assertTrue(hinausgeworfen.isEmpty());
        assertTrue(ablage.geltende(BERT, Strafart.BANN, MITTAGS).isPresent(),
                "die Strafe steht trotzdem");
    }

    @Test
    @DisplayName("15 - die ANSAGE geht an ALLE und nennt den Grund")
    void ansageGehtAnAlle() throws CommandSyntaxException {
        anwesend.add(CLARA);

        tippe("strafe bann Bert 1h Grief in Altfurt");

        assertEquals(1, angesagt.size(), angesagt.toString());
        assertTrue(angesagt.get(0).contains("Grief in Altfurt"),
                "wer zusieht, wie jemand grieft, und nichts geschieht, meldet beim"
                        + " naechsten Mal nicht: " + angesagt.get(0));
    }

    @Test
    @DisplayName("16 - mit ansagen: false sagt niemand etwas")
    void ohneAnsage() throws CommandSyntaxException {
        anwesend.add(CLARA);

        tippe("strafe bann Bert 1h Grief", moderator, false,
                Strafwerte.VORGABE.mitAnsagen(false));

        assertTrue(angesagt.isEmpty(), angesagt.toString());
    }

    @Test
    @DisplayName("16b - eine LEERE Ansage sendet NICHTS, nicht eine leere Zeile")
    void leereAnsageSendetNichts() throws CommandSyntaxException {
        // DER FUND VOM 25.11.2026: config.yml und Sprachvorlage nennen das Leeren des
        // Wortlauts ausdruecklich als zweiten Abschaltweg - gefragt wurde es nicht.
        // Sprache.teile haengt am Ende immer eine Zeile an, ein leerer Wortlaut ergibt
        // also genau ein LEERES Component, und eineZeile wirft dabei nicht. Von da an
        // bekam bei jeder Strafe jeder Anwesende eine leere Chatzeile statt keiner.
        //
        // Geprueft mit der Sprache aus einer Datei mit geleerten Ansagen - der Weg,
        // den die Dateien beschreiben.
        anwesend.add(CLARA);
        sprache = Sprachprobe.mitZeilen(java.util.Map.of(
                "strafe.ansage", "",
                "strafe.ansage-ohne-dauer", "",
                "strafe.ansage-aufhebung", ""));

        tippe("strafe bann Bert 1h Grief in Altfurt");
        tippe("strafe rauswurf Bert Spam");

        assertTrue(angesagt.isEmpty(),
                "ein geleerter Wortlaut heisst KEINE Zeile: " + angesagt);
    }

    // ------------------------------------------------------------- aufheben

    @Test
    @DisplayName("17 - /strafe aufheben nimmt eine Strafe zurueck")
    void aufhebenNimmtZurueck() throws CommandSyntaxException {
        long nummer = ablage.lege(BERT, Strafart.BANN, "Grief", null, MITTAGS,
                new Strafdauer(Duration.ofHours(1L))).nummer();

        List<String> zeilen = tippe("strafe aufheben " + nummer
                + " Einspruch angenommen");

        assertTrue(zeilen.get(0).contains(String.valueOf(nummer)), zeilen.get(0));
        assertTrue(ablage.geltende(BERT, Strafart.BANN, MITTAGS).isEmpty());
        assertEquals("Einspruch angenommen",
                ablage.lies(nummer).orElseThrow().aufhebungsgrund());
    }

    @Test
    @DisplayName("18 - die Absage beim Aufheben nennt nicht die Art, sondern die Tat")
    void absageBeimAufhebenNenntDieTat() throws CommandSyntaxException {
        // Der Fund vom 25.11.2026: Der Rueckfall auf das Wort "Sperre" liess einen
        // Moderator lesen "Fuer Sperre fehlt dir das Recht", waehrend er aufheben
        // wollte. Ein eigener Satz je Zweig.
        long nummer = ablage.lege(BERT, Strafart.BANN, "Grief", null, MITTAGS,
                Strafdauer.UNBEGRENZT).nummer();

        List<String> zeilen = tippe("strafe aufheben " + nummer + " zu hart");

        assertTrue(zeilen.get(0).contains(String.valueOf(nummer)),
                "die Absage nennt die Nummer: " + zeilen.get(0));
        assertFalse(zeilen.get(0).contains("Sperre"),
                "und NICHT das Wort fuer die Art: " + zeilen.get(0));
        assertFalse(ablage.lies(nummer).orElseThrow().istAufgehoben(),
                "eine unbegrenzte Strafe hebt nur auf, wer dauerhaft sperren darf");
    }

    @Test
    @DisplayName("19 - eine unbekannte Nummer wird genannt")
    void unbekannteNummer() throws CommandSyntaxException {
        List<String> zeilen = tippe("strafe aufheben 99 Einspruch");

        assertTrue(zeilen.get(0).contains("99"), zeilen.get(0));
    }

    // ------------------------------------------------------------- Auskunft

    @Test
    @DisplayName("20 - /strafe verlauf nennt jede Strafe mit Art, Stand und Grund")
    void verlaufNenntAlles() throws CommandSyntaxException {
        ablage.lege(BERT, Strafart.RAUSWURF, "Spam im Chat", null, MITTAGS, null);
        ablage.lege(BERT, Strafart.BANN, "Grief in Altfurt", null,
                MITTAGS.plusSeconds(60L), new Strafdauer(Duration.ofHours(1L)));

        List<String> zeilen = tippe("strafe verlauf Bert");

        // Kopf, zwei Zeilen, Hinweis.
        assertEquals(4, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(1).contains("Grief in Altfurt"),
                "die NEUESTE zuerst: " + zeilen.get(1));
        assertTrue(zeilen.get(1).contains("gilt"), zeilen.get(1));
        assertTrue(zeilen.get(2).contains("Spam im Chat"), zeilen.get(2));
        assertTrue(zeilen.get(2).contains("abgelaufen"),
                "ein Rauswurf ist vorbei, sobald er geschieht: " + zeilen.get(2));
    }

    @Test
    @DisplayName("21 - ein Spieler ohne Strafen bekommt einen eigenen Satz")
    void verlaufLeer() throws CommandSyntaxException {
        List<String> zeilen = tippe("strafe verlauf Bert");

        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("Bert"), zeilen.get(0));
    }

    @Test
    @DisplayName("22 - /bannliste zeigt die geltenden Banne mit Grund und Dauer")
    void bannlisteZeigtGrundUndDauer() throws CommandSyntaxException {
        ablage.lege(BERT, Strafart.BANN, "Grief in Altfurt", null, MITTAGS,
                new Strafdauer(Duration.ofHours(1L)));
        ablage.lege(CLARA, Strafart.STUMM, "Beleidigung", null, MITTAGS,
                new Strafdauer(Duration.ofHours(1L)));

        List<String> zeilen = tippe("bannliste");

        // Plan 12c.4: Grund UND Dauer, oeffentlich. Und nur die BANNE - eine
        // oeffentliche Liste der Stummgeschalteten waere ein Pranger fuer eine
        // Strafe, die in zehn Minuten vorbei ist.
        assertEquals(2, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(1).contains("Bert"), zeilen.get(1));
        assertTrue(zeilen.get(1).contains("Grief in Altfurt"), zeilen.get(1));
        assertFalse(String.join(" ", zeilen).contains("Beleidigung"),
                "eine Stummschaltung steht nicht in der Bannliste: " + zeilen);
    }

    @Test
    @DisplayName("23 - eine leere Bannliste sagt es, statt nichts zu senden")
    void bannlisteLeer() throws CommandSyntaxException {
        List<String> zeilen = tippe("bannliste");

        assertEquals(1, zeilen.size(), zeilen.toString());
        assertFalse(zeilen.get(0).isBlank());
    }

    @Test
    @DisplayName("24 - mit bannliste-oeffentlich: false liest sie nur das Team")
    void bannlisteNichtOeffentlich() throws CommandSyntaxException {
        ablage.lege(BERT, Strafart.BANN, "Grief", null, MITTAGS,
                new Strafdauer(Duration.ofHours(1L)));
        Strafwerte zu = Strafwerte.VORGABE.mitBannlisteOeffentlich(false);
        Spielerprobe jeder = new Spielerprobe("Gast", Spielerprobe.kennungAus(855),
                StrafBefehle.RECHT_BANNLISTE);

        List<String> fuerJeden = tippe("bannliste", jeder, false, zu);
        List<String> fuerDasTeam = tippe("bannliste", moderator, false, zu);

        // Eine leere Liste waere die falsche Antwort: Sie behauptet, niemand sei
        // gesperrt.
        assertEquals(1, fuerJeden.size(), fuerJeden.toString());
        assertFalse(fuerJeden.get(0).contains("Bert"), fuerJeden.get(0));
        assertTrue(String.join(" ", fuerDasTeam).contains("Bert"),
                fuerDasTeam.toString());
    }

    // ------------------------------------------------------------- Deckung

    @Test
    @DisplayName("25 - DIE DECKUNG: jede Strafart hat ihr Wort in der Vorlage")
    void jedeArtHatIhrWort() {
        // Diese Probe ist an ZWEI Stellen zugesagt - in Sprachschluessel.familienDeckung
        // und in SendewegTest 33x. Beide erklaeren die drei Artworte fuer
        // selbstbescheinigend und verweisen hierher. Ohne sie waere das eine Zusage ohne
        // Bau, und eine Art ohne Wort stuende im Spiel mit ihrem Pfad da.
        for (Strafart art : Strafart.values()) {
            String wort = Straftext.artwort(sprache, art);
            assertFalse(wort.isBlank(), art + " hat kein Wort");
            assertFalse(wort.startsWith("strafe."),
                    art + " liefert seinen PFAD statt eines Wortes: " + wort);
        }
        // Und sie muessen sich UNTERSCHEIDEN: Waeren zwei gleich, koennte die Liste die
        // Angabe ebenso gut weglassen - und ein Moderator saehe nicht, ob jemand
        // gebannt oder nur stummgeschaltet ist.
        assertEquals(Strafart.values().length,
                java.util.Arrays.stream(Strafart.values())
                        .map(art -> Straftext.artwort(sprache, art))
                        .distinct().count(),
                "zwei Arten mit demselben Wort");
    }

    @Test
    @DisplayName("26 - die drei Staende haben drei verschiedene Worte")
    void dreiStaendeDreiWorte() {
        Strafe gilt = Strafe.neu(1L, BERT, Strafart.BANN, "Grief", null, MITTAGS,
                new Strafdauer(Duration.ofHours(1L)));
        Strafe abgelaufen = Strafe.neu(2L, BERT, Strafart.RAUSWURF, "Spam", null,
                MITTAGS, null);
        Strafe frei = gilt.aufgehoben(null, "Einspruch", MITTAGS.plusSeconds(60L));

        String wortGilt = Straftext.standwort(sprache, gilt, MITTAGS);
        String wortAb = Straftext.standwort(sprache, abgelaufen, MITTAGS);
        String wortFrei = Straftext.standwort(sprache, frei, MITTAGS);

        assertEquals(3, List.of(wortGilt, wortAb, wortFrei).stream().distinct()
                .count(), "drei Staende, drei Worte: " + wortGilt + "/" + wortAb
                + "/" + wortFrei);
        // Die REIHENFOLGE der Fragen: Eine aufgehobene Strafe, deren Zeit auch
        // abgelaufen ist, ist vor allem zurueckgenommen - das ist die Auskunft, nach
        // der ein zweiter Moderator fragt.
        assertEquals(wortFrei, Straftext.standwort(sprache, frei,
                MITTAGS.plusSeconds(99999L)),
                "aufgehoben bleibt aufgehoben, auch nach Ablauf der Frist");
    }

    @Test
    @DisplayName("27 - die Dauer steht in vier Groessenordnungen, und sie unterscheiden"
            + " sich")
    void dauerInVierGroessenordnungen() {
        assertTrue(Straftext.dauerwort(sprache,
                new Strafdauer(Duration.ofSeconds(30L))).contains("30"), "Sekunden");
        assertTrue(Straftext.dauerwort(sprache,
                new Strafdauer(Duration.ofMinutes(10L))).contains("10"), "Minuten");
        assertTrue(Straftext.dauerwort(sprache,
                new Strafdauer(Duration.ofHours(5L))).contains("5"), "Stunden");
        assertTrue(Straftext.dauerwort(sprache,
                new Strafdauer(Duration.ofDays(7L))).contains("7"), "Tage");
        // Eine unbegrenzte Dauer gibt ein WORT und keine Zahl: Ein Datum im Jahr 2126
        // liest sich wie eine Frist.
        String ohne = Straftext.dauerwort(sprache, Strafdauer.UNBEGRENZT);
        assertFalse(ohne.matches(".*\\d.*"), "dauerhaft ohne Zahl: " + ohne);
        assertEquals(ohne, Straftext.dauerwort(sprache, null),
                "auch null heisst dauerhaft");
    }

    @Test
    @DisplayName("28 - die Konsole hat ein Wort und keine leere Stelle")
    void konsoleHatEinWort() {
        String wort = Straftext.werwort(sprache, Optional.empty(),
                StrafBefehlTest::nameVon);

        assertFalse(wort.isBlank(),
                "ohne dieses Wort stuende in der Liste eine leere Stelle");
        assertEquals("Bert", Straftext.werwort(sprache, Optional.of(BERT),
                StrafBefehlTest::nameVon));
    }

    // ------------------------------------------------------------- Werkzeug

    private List<String> tippe(String eingabe) throws CommandSyntaxException {
        return tippe(eingabe, moderator);
    }

    private List<String> tippe(String eingabe, Spielerprobe wer)
            throws CommandSyntaxException {
        return tippe(eingabe, wer, false, Strafwerte.VORGABE);
    }

    private List<String> tippe(String eingabe, Spielerprobe wer, boolean teamGestoert)
            throws CommandSyntaxException {
        return tippe(eingabe, wer, teamGestoert, Strafwerte.VORGABE);
    }

    private List<String> tippe(String eingabe, Spielerprobe wer, boolean teamGestoert,
                               Strafwerte werte) throws CommandSyntaxException {
        baum(werte, teamGestoert).tippe(eingabe, wer);
        return wer.chatBild();
    }

    /**
     * Der Baum.
     *
     * @param teamGestoert ob die Teamfrage wirft - dann gilt der Betroffene als
     *                     Teammitglied, denn von zwei Fehlern nimmt man den, der sich
     *                     zuruecknehmen laesst
     */
    private Befehlsprobe baum(Strafwerte werte, boolean teamGestoert) {
        StrafBefehle befehle = new StrafBefehle(
                new Strafbuch(ablage, () -> werte, StrafBefehle.RECHTE,
                        wer -> teamGestoert || imTeam.contains(wer)), sprache,
                StrafBefehlTest::kennungVon,
                wer -> teamGestoert || imTeam.contains(wer),
                () -> {
                    List<Audience> kreis = new ArrayList<>();
                    for (UUID wer : anwesend) {
                        kreis.add(new Hoerer(wer, angesagt));
                    }
                    return kreis;
                },
                new StrafBefehle.Spielerhand() {
                    @Override
                    public boolean wirfHinaus(UUID wer, Component zeile) {
                        if (!anwesend.contains(wer)) {
                            return false;
                        }
                        hinausgeworfen.add(Sprachprobe.bild(zeile));
                        return true;
                    }

                    @Override
                    public boolean sage(UUID wer, Component zeile) {
                        if (!anwesend.contains(wer)) {
                            return false;
                        }
                        gesagt.add(Sprachprobe.bild(zeile));
                        return true;
                    }
                },
                StrafBefehlTest::nameVon, () -> MITTAGS, () -> ZONE,
                Logger.getLogger("StrafBefehlProbe-" + UUID.randomUUID()));
        return new Befehlsprobe(List.of(befehle.strafe(), befehle.bannliste()));
    }

    /** Ein Zuhoerer der Ansage - er schreibt mit, was er liest. */
    private record Hoerer(UUID wer, List<String> mitschrift) implements Audience {

        @Override
        public void sendMessage(Component zeile) {
            mitschrift.add(Sprachprobe.bild(zeile));
        }
    }

    private static UUID kennungVon(String name) {
        return switch (name) {
            case "Bert" -> BERT;
            case "Clara" -> CLARA;
            default -> null;
        };
    }

    private static String nameVon(UUID wer) {
        if (BERT.equals(wer)) {
            return "Bert";
        }
        if (CLARA.equals(wer)) {
            return "Clara";
        }
        return "?" + wer;
    }
}
