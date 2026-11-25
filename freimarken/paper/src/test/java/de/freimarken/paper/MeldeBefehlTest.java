package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Meldebuch;
import de.freimarken.kern.Meldewerte;
import de.freimarken.kern.Meldung;
import de.freimarken.kern.SpeicherMeldungen;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /melden} GETIPPT - die Zusage und jede Absage.
 *
 * <p><b>Jede Zeile hier liest ein Spieler im Augenblick eines Vorfalls.</b> Das ist der
 * Grund, aus dem dieser Befehl so viele eigene Absagen hat: Wer eine Absage liest, die
 * nicht sagt, was zu aendern ist, versucht es ein zweites Mal und danach nicht mehr -
 * und Plan 12c.3 nennt genau das als das Ende eines Meldewesens.
 *
 * <p>Der ORT, der ANSAGEKREIS und die UHR sind Naehte. Darum laeuft hier jeder Zweig
 * durch, auch der, der im Spiel einen laufenden Server mit mehreren Spielern braeuchte.
 */
@DisplayName("/melden getippt")
class MeldeBefehlTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    private static final UUID BERT = Spielerprobe.kennungAus(902);

    /** Die Namen, die dieser Server kennt - die Naht statt {@code Bukkit}. */
    private static final Map<String, UUID> BEKANNT =
            new LinkedHashMap<>(Map.of("Bert", BERT));

    private SpeicherMeldungen ablage;
    private Sprache sprache;
    private Spielerprobe anna;
    private Spielerprobe moderator;
    private Instant jetzt;

    @BeforeAll
    static void server() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void frisch() {
        ablage = new SpeicherMeldungen();
        sprache = Sprachprobe.spracheAusDemJar();
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(901),
                MeldeBefehle.RECHT);
        moderator = new Spielerprobe("Chef", Spielerprobe.kennungAus(903),
                MeldungBefehle.RECHT);
        jetzt = MITTAGS;
    }

    @Test
    @DisplayName("1 - eine Meldung wird angelegt, und die Zusage nennt Nummer und Namen")
    void meldungWirdAngelegt() throws CommandSyntaxException {
        tippe("melden Bert baut auf meiner Parzelle");

        List<String> zeilen = anna.chatBild();
        assertEquals(1, zeilen.size(), zeilen.toString());
        assertTrue(zeilen.get(0).contains("Bert"),
                "der Name gehoert in die Zusage: " + zeilen.get(0));
        assertEquals(1, ablage.offeneVon(anna.spieler().getUniqueId()));
        Meldung eine = ablage.offene(1).get(0);
        assertEquals("baut auf meiner Parzelle", eine.grund(),
                "der Grund ist ein greedyString - ein Satz, kein Wort");
        assertEquals(MITTAGS, eine.gemeldetAm());
    }

    @Test
    @DisplayName("2 - der TATORT ist der Ort des MELDERS")
    void tatortIstDerOrtDesMelders() throws CommandSyntaxException {
        tippe("melden Bert Griefing an der Mauer");

        // Was er gesehen hat, hat er von DORT gesehen. Wer eine Stunde spaeter zum
        // Betroffenen springt, findet den Spieler, aber nicht die Tat.
        Meldung eine = ablage.offene(1).get(0);
        assertEquals("altfurt", eine.welt());
        assertEquals(10, eine.x());
        assertEquals(-60, eine.y());
        assertEquals(-20, eine.z());
    }

    @Test
    @DisplayName("3 - DIE ANSAGE geht an anwesende Moderatoren, nicht an alle")
    void ansageGehtAnModeratoren() throws CommandSyntaxException {
        tippe("melden Bert Vorfall im Hafen");

        // Der Ansagekreis ist eine Naht, und die Probe kann darum zaehlen, wer sie
        // bekommen haette. Im Spiel steht dahinter eine Schleife ueber die angemeldeten
        // Spieler mit dem Recht.
        List<String> gelesen = moderator.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("Anna"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("Bert"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("Vorfall im Hafen"), gelesen.get(0));
    }

    @Test
    @DisplayName("4 - ein LEERER Ansagesatz heisst: keine Ansage")
    void leereAnsageSchweigt() throws CommandSyntaxException {
        // Der Wortlaut IST der Schalter - dieselbe Entscheidung wie bei den Ansagen des
        // Angelwettbewerbs. Auf einem Server mit zehn Moderatoren ist eine Zeile je
        // Meldung eine Belaestigung, und /meldung gibt es ohnehin.
        Sprache ohneAnsage = Sprachprobe.mitZeilen(
                Map.of(MeldeSatz.ANSAGE.pfad(), ""));

        baum(Meldewerte.VORGABE, ohneAnsage).tippe("melden Bert Vorfall", anna);

        assertEquals(List.of(), moderator.chatBild(),
                "ein leerer Satz sendet keine leere Zeile");
        assertEquals(1, ablage.offeneVon(anna.spieler().getUniqueId()),
                "die Meldung selbst entsteht trotzdem");
    }

    @Test
    @DisplayName("5 - ein zu kurzer Grund nennt die MINDESTLAENGE")
    void grundZuKurz() throws CommandSyntaxException {
        tippe("melden Bert ab");

        assertTrue(anna.chatBild().get(0).contains(
                String.valueOf(Meldung.KUERZESTER_GRUND)),
                "die Zahl gehoert in die Absage: " + anna.chatBild().get(0));
        assertEquals(0, ablage.offeneVon(anna.spieler().getUniqueId()));
    }

    @Test
    @DisplayName("6 - ein zu langer Grund nennt die HOECHSTLAENGE")
    void grundZuLang() throws CommandSyntaxException {
        tippe("melden Bert " + "x".repeat(Meldung.LAENGSTER_GRUND + 10));

        assertTrue(anna.chatBild().get(0).contains(
                String.valueOf(Meldung.LAENGSTER_GRUND)), anna.chatBild().get(0));
        assertEquals(0, ablage.offeneVon(anna.spieler().getUniqueId()));
    }

    @Test
    @DisplayName("7 - ein Abschnittszeichen im Grund bekommt seinen EIGENEN Satz")
    void grundMitZeichen() throws CommandSyntaxException {
        tippe("melden Bert harmlos§cFEHLER");

        // DREI Saetze fuer drei Faelle: Der Spieler soll wissen, was er aendern muss.
        // Hier darf NICHT die Laengenabsage stehen - sonst kuerzt er den Grund und
        // versucht es wieder.
        String zeile = anna.chatBild().get(0);
        assertTrue(zeile.contains("Zeichen"), zeile);
        assertFalse(zeile.contains(String.valueOf(Meldung.KUERZESTER_GRUND)),
                "nicht die Laengenabsage: " + zeile);
        assertEquals(0, ablage.offeneVon(anna.spieler().getUniqueId()));
    }

    @Test
    @DisplayName("8 - ERST der Grund, dann der Name")
    void erstDerGrundDannDerName() throws CommandSyntaxException {
        // Beides falsch. Gelesen werden soll der GRUND: Er steht schon da, und wer ihn
        // nachbessern muss, soll das erfahren, bevor er den Namen nachschlaegt.
        // Umgekehrt als bei /team gib - dort ist der Name der Fehler, der eine Rolle an
        // einen Geisterspieler vergibt; hier vergibt eine Meldung nichts.
        tippe("melden Niemand ab");

        assertTrue(anna.chatBild().get(0).contains(
                String.valueOf(Meldung.KUERZESTER_GRUND)), anna.chatBild().get(0));
    }

    @Test
    @DisplayName("9 - ein unbekannter Spieler wird abgewiesen")
    void unbekannterSpieler() throws CommandSyntaxException {
        tippe("melden Niemand ein brauchbarer Grund");

        assertTrue(anna.chatBild().get(0).contains("Niemand"), anna.chatBild().get(0));
        assertEquals(0, ablage.offeneVon(anna.spieler().getUniqueId()));
    }

    @Test
    @DisplayName("10 - sich selbst melden geht nicht")
    void selbstmeldung() throws CommandSyntaxException {
        BEKANNT.put("Anna", anna.spieler().getUniqueId());
        try {
            tippe("melden Anna ich bin es selbst");

            assertTrue(anna.chatBild().get(0).contains("selbst"), anna.chatBild().get(0));
            assertEquals(0, ablage.offeneVon(anna.spieler().getUniqueId()));
        } finally {
            BEKANNT.remove("Anna");
        }
    }

    @Test
    @DisplayName("11 - DIE ABKLINGZEIT nennt die RESTZEIT")
    void abklingzeit() throws CommandSyntaxException {
        tippe("melden Bert erster Vorfall");
        anna = new Spielerprobe("Anna", anna.spieler().getUniqueId(),
                MeldeBefehle.RECHT);
        jetzt = MITTAGS.plusSeconds(20L);

        tippe("melden Bert zweiter Vorfall");

        // 40 Sekunden bei 60 Sekunden Abklingzeit. Eine Absage ohne Zahl laesst ihn
        // raten, und er versucht es sofort wieder.
        assertTrue(anna.chatBild().get(0).contains("40"), anna.chatBild().get(0));
        assertEquals(1, ablage.offeneVon(anna.spieler().getUniqueId()));
    }

    @Test
    @DisplayName("12 - DIE HOECHSTZAHL nennt die Grenze und sagt, dass es weitergeht")
    void hoechstzahl() throws CommandSyntaxException {
        Meldewerte ohneWarten = Meldewerte.VORGABE.mitAbklingzeit(0L);
        for (int nummer = 0; nummer < 3; nummer++) {
            baum(ohneWarten, sprache).tippe("melden Bert Vorfall " + nummer, anna);
        }
        Spielerprobe nochmal = new Spielerprobe("Anna", anna.spieler().getUniqueId(),
                MeldeBefehle.RECHT);

        baum(ohneWarten, sprache).tippe("melden Bert vierter Vorfall", nochmal);

        String zeile = nochmal.chatBild().get(0);
        assertTrue(zeile.contains("3"), "die Grenze gehoert hinein: " + zeile);
        assertTrue(zeile.contains("bearbeitet"),
                "und dass es weitergeht, sobald eine erledigt ist: " + zeile);
        assertEquals(3, ablage.offeneVon(anna.spieler().getUniqueId()));
    }

    @Test
    @DisplayName("13 - ist das Meldewesen aus, sagt der Befehl das")
    void meldewesenAus() throws CommandSyntaxException {
        baum(Meldewerte.VORGABE.mitAn(false), sprache)
                .tippe("melden Bert Vorfall", anna);

        assertTrue(anna.chatBild().get(0).contains("nicht gemeldet"),
                anna.chatBild().get(0));
        assertEquals(0, ablage.offeneVon(anna.spieler().getUniqueId()));
    }

    @Test
    @DisplayName("14 - die Konsole kann nicht melden - sie steht nirgends")
    void konsoleKannNichtMelden() throws CommandSyntaxException {
        Befehlsprobe baum = baum(Meldewerte.VORGABE, sprache);

        baum.tippeAufDerKonsole("melden Bert Vorfall");

        // Nicht aus Prinzip: Eine Meldung hat einen ORT, weil der Moderator hinspringt.
        assertTrue(baum.konsoleBild().get(0).contains("Spieler"),
                baum.konsoleBild().get(0));
        assertEquals(0, ablage.offene(10).size());
    }

    @Test
    @DisplayName("15 - jeder Zweig antwortet, auch ohne sein Argument")
    void jederZweigAntwortet() throws CommandSyntaxException {
        // Ohne das eigene executes auf dem Wort und auf dem Spielerargument endet
        // /melden beziehungsweise /melden Bert in Brigadiers englischem "Unknown or
        // incomplete command" - eine rote Zeile fuer jemanden, der gerade einen Vorfall
        // gesehen hat.
        tippe("melden");
        assertFalse(anna.chatBild().isEmpty(), "melden ohne alles");

        Spielerprobe zweite = new Spielerprobe("Anna2", Spielerprobe.kennungAus(904),
                MeldeBefehle.RECHT);
        baum(Meldewerte.VORGABE, sprache).tippe("melden Bert", zweite);
        assertFalse(zweite.chatBild().isEmpty(), "melden mit Namen, ohne Grund");
    }

    @Test
    @DisplayName("16 - ohne das Recht gibt es den Befehl nicht")
    void ohneRechtKeinBefehl() {
        Spielerprobe ohne = new Spielerprobe("Gast", Spielerprobe.kennungAus(905));

        assertThrows(CommandSyntaxException.class,
                () -> baum(Meldewerte.VORGABE, sprache).tippe("melden Bert Vorfall",
                        ohne));
    }

    @Test
    @DisplayName("17 - ohne Buch, Sprache, Naht, Ort, Kreis, Uhr oder Protokoll nichts")
    void riegel() {
        Meldebuch buch = new Meldebuch(ablage, () -> Meldewerte.VORGABE,
                MeldungBefehle.RECHT);
        MeldeBefehle.Namenssuche suche = BEKANNT::get;
        MeldeBefehle.Ortsgeber orte = spieler -> new MeldeBefehle.Ort("altfurt", 0, 0, 0);
        MeldeBefehle.Ansagekreis kreis = List::of;
        Meldetext.Namensgeber namen = wer -> "x";
        MeldeBefehle.Uhr uhr = () -> MITTAGS;
        Logger stumm = Logger.getLogger("MeldeBefehlRiegel");

        assertThrows(IllegalArgumentException.class, () -> new MeldeBefehle(null, sprache,
                suche, orte, kreis, namen, uhr, stumm));
        assertThrows(IllegalArgumentException.class, () -> new MeldeBefehle(buch, null,
                suche, orte, kreis, namen, uhr, stumm));
        assertThrows(IllegalArgumentException.class, () -> new MeldeBefehle(buch, sprache,
                null, orte, kreis, namen, uhr, stumm));
        assertThrows(IllegalArgumentException.class, () -> new MeldeBefehle(buch, sprache,
                suche, null, kreis, namen, uhr, stumm));
        assertThrows(IllegalArgumentException.class, () -> new MeldeBefehle(buch, sprache,
                suche, orte, null, namen, uhr, stumm));
        assertThrows(IllegalArgumentException.class, () -> new MeldeBefehle(buch, sprache,
                suche, orte, kreis, null, uhr, stumm));
        assertThrows(IllegalArgumentException.class, () -> new MeldeBefehle(buch, sprache,
                suche, orte, kreis, namen, null, stumm));
        assertThrows(IllegalArgumentException.class, () -> new MeldeBefehle(buch, sprache,
                suche, orte, kreis, namen, uhr, null));
    }

    private void tippe(String eingabe) throws CommandSyntaxException {
        baum(Meldewerte.VORGABE, sprache).tippe(eingabe, anna);
    }

    /**
     * Der Baum.
     *
     * <p>Die UHR liest das Feld {@code jetzt} bei jedem Aufruf und nicht einmal beim
     * Bauen: So kann eine Probe die Zeit zwischen zwei Befehlen verstellen, ohne den
     * Baum neu zu bauen - und genau das braucht die Abklingzeit.
     */
    private Befehlsprobe baum(Meldewerte werte, Sprache welcheSprache) {
        return new Befehlsprobe(List.of(new MeldeBefehle(
                new Meldebuch(ablage, () -> werte, MeldungBefehle.RECHT), welcheSprache,
                BEKANNT::get,
                spieler -> new MeldeBefehle.Ort("altfurt", 10, -60, -20),
                () -> {
                    List<Audience> kreis = new ArrayList<>();
                    kreis.add(moderator.spieler());
                    return kreis;
                },
                MeldeBefehlTest::nameVon, () -> jetzt,
                Logger.getLogger("MeldeBefehlProbe-" + UUID.randomUUID())).melden()));
    }

    private static String nameVon(UUID wer) {
        for (Map.Entry<String, UUID> eintrag : BEKANNT.entrySet()) {
            if (eintrag.getValue().equals(wer)) {
                return eintrag.getKey();
            }
        }
        if (Spielerprobe.kennungAus(901).equals(wer)) {
            return "Anna";
        }
        return wer.toString().substring(0, Spielername.ZEICHEN_DER_KENNUNG);
    }
}
