package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /hilfe} GETIPPT - und die Liste fuer jede Rechtelage.
 *
 * <p>Dieselbe Bauart wie {@link RangBefehlTest}: Der Baum wird gebaut und der Befehl
 * getippt. Die LISTE selbst wird zusaetzlich unmittelbar gebaut, und zwar fuer drei
 * Rechtelagen - das geht nur so, weil {@code HilfeBefehle.zeilen} eine Rechtefrage
 * hereinbekommt und keinen Spieler.
 *
 * <p><b>Geprueft wird die GANZE Antwort und nicht ein contains:</b> Eine Zeile zu wenig
 * ist ein Befehl, von dem ein Neuling nie erfaehrt; eine Zeile zu viel ist ein Befehl,
 * den er tippt und nicht darf. Beides sieht ein contains nicht.
 */
@DisplayName("/hilfe getippt")
class HilfeBefehlTest {

    private static final UUID DORA = Spielerprobe.kennungAus(213);

    /** Die ganze Liste - jeder Befehl, jede Gruppe. */
    private static final List<String> ALLES = List.of(
                "&#c6a15bAlle Befehle des Freimarkenbundes",
                        "&#edf1f3Grund und Bauen",
                        "&#e3c57e/parzelle&#6e7a82 - &#a5b4bfParzellen nehmen, ansehen und "
                                + "verwalten",
                        "&#e3c57e/laden&#6e7a82 - &#a5b4bfDie Markthalle und ihre "
                                + "Ladeneinheiten",
                        "&#edf1f3Geld und Handel",
                        "&#e3c57e/mark&#6e7a82 - &#a5b4bfDein Guthaben in Mark",
                        "&#e3c57e/markt&#6e7a82 - &#a5b4bfWas der Bundesmarkt ankauft - "
                                + "verkauft wird an seinen Schildern",
                        "&#e3c57e/shop&#6e7a82 - &#a5b4bfDeine Kistenshops auflisten und fuer "
                                + "andere freigeben",
                        "&#edf1f3Rang und Beruf",
                        "&#e3c57e/rang&#6e7a82 - &#a5b4bfDein Rang, die naechste Stufe und die "
                                + "ganze Leiter",
                        "&#e3c57e/beruf&#6e7a82 - &#a5b4bfDeinen Beruf waehlen, ansehen und "
                                + "aufgeben",
                        "&#edf1f3Reisen und Treffen",
                        "&#e3c57e/warp&#6e7a82 - &#a5b4bfReisen zwischen den Welten des "
                                + "Freimarkenbunds",
                        "&#e3c57e/zurueck&#6e7a82 - &#a5b4bfZurueck zum letzten Ort - nach Tod, "
                                + "Reise oder Teleport",
                        "&#e3c57e/tpa&#6e7a82 - &#a5b4bfFrage einen Spieler, ob du zu ihm "
                                + "kommen darfst",
                        "&#e3c57e/tpahere&#6e7a82 - &#a5b4bfLade einen Spieler ein, zu dir zu "
                                + "kommen",
                        "&#e3c57e/tpaccept&#6e7a82 - &#a5b4bfEine Teleportanfrage annehmen",
                        "&#e3c57e/tpdeny&#6e7a82 - &#a5b4bfTeleportanfragen ablehnen",
                        "&#edf1f3Eigene Welten",
                        "&#e3c57e/freistadt&#6e7a82 - &#a5b4bfDie Freistaedte - Liste, "
                                + "gruenden, Grundstuecke, Stadtbuerger, Regeln, Kasse",
                        "&#e3c57e/werder&#6e7a82 - &#a5b4bfMeerinseln auf Hafenwerder ansehen "
                                + "und eine nehmen",
                        "&#e3c57e/wolke&#6e7a82 - &#a5b4bfdie eigenen Wolkeninseln, eine "
                                + "nehmen, heimreisen",
                        "&#edf1f3Kleine Spiele in Altfurt",
                        "&#e3c57e/lotterie&#6e7a82 - &#a5b4bfEin Los ziehen - das erste am Tag ist gratis",
                        "&#e3c57e/wettbewerb&#6e7a82 - &#a5b4bfDer Angelwettbewerb - Stand, Rangliste, anmelden",
                        "&#edf1f3Der Bund",
                        "&#e3c57e/menu&#6e7a82 - &#a5b4bfDas Hauptmenue - jeder Befehl mit "
                                + "einem Klick",
                        "&#e3c57e/hilfe&#6e7a82 - &#a5b4bfAlle Befehle, nach Bereichen geordnet",
                        "&#e3c57e/regeln&#6e7a82 - &#a5b4bfWas auf diesem Server gilt - mit /regeln chat im Chat",
                        "&#e3c57e/freimarken&#6e7a82 - &#a5b4bfStand, Preise und Welt des "
                                + "Freimarkenbunds",
                        // Schritt 16a Teil A: /team, in derselben Gruppe wie
                        // /freimarken. In OHNE_RECHTE und NUR_RANG steht die
                        // Zeile mit Absicht NICHT - dort ist ihr Fehlen die
                        // Zusage, denn freimarken.team gehoert ab Werk nur
                        // Operatoren.
                        "&#e3c57e/team&#6e7a82 - &#a5b4bfTeamrollen ansehen, vergeben und entziehen",
                        // Schritt 16a Teil C: /melden gehoert JEDEM (siehe Hilfeplan),
                        // /meldung haengt an freimarken.meldungen und steht nur in
                        // dieser Liste, weil hier jedes Recht gesetzt ist.
                        "&#e3c57e/melden&#6e7a82 - &#a5b4bfEinen Spieler melden - mit Grund, ein Moderator sieht es sich an",
                        "&#e3c57e/meldung&#6e7a82 - &#a5b4bfOffene Meldungen ansehen, annehmen und erledigen",
                        // Schritt 16a Teil B: /strafe haengt am Recht fuer die Sperre
                        // (das schwerste der drei Artrechte), /bannliste gehoert
                        // JEDEM - darum steht sie auch in den zwei Listen darunter.
                        "&#e3c57e/strafe&#6e7a82 - &#a5b4bfStummschalten, hinauswerfen, sperren - mit Dauer und Grund",
                        "&#e3c57e/bannliste&#6e7a82 - &#a5b4bfWer gesperrt ist, warum und bis wann",
                        "&#6e7a82Jeder Befehl ohne Zusatz zeigt, was er kann.");

    /** Was ein Spieler OHNE jedes Recht sieht: drei Befehle in zwei Gruppen. */
    private static final List<String> OHNE_RECHTE = List.of(
                "&#c6a15bAlle Befehle des Freimarkenbundes",
                        "&#edf1f3Grund und Bauen",
                        "&#e3c57e/parzelle&#6e7a82 - &#a5b4bfParzellen nehmen, ansehen und "
                                + "verwalten",
                        "&#edf1f3Geld und Handel",
                        "&#e3c57e/mark&#6e7a82 - &#a5b4bfDein Guthaben in Mark",
                        "&#edf1f3Der Bund",
                        "&#e3c57e/freimarken&#6e7a82 - &#a5b4bfStand, Preise und Welt des "
                                + "Freimarkenbunds",
                        // /melden und /bannliste stehen AUCH hier: Beide Eintraege
                        // tragen JEDER, und das gilt unabhaengig von jedem Recht -
                        // genau wie /parzelle, /mark und /freimarken darueber. Plan
                        // 12c.4 macht die Bannliste ausdruecklich oeffentlich.
                        "&#e3c57e/melden&#6e7a82 - &#a5b4bfEinen Spieler melden - mit Grund, ein Moderator sieht es sich an",
                        "&#e3c57e/bannliste&#6e7a82 - &#a5b4bfWer gesperrt ist, warum und bis wann",
                        "&#6e7a82Jeder Befehl ohne Zusatz zeigt, was er kann.");

    /** Dieselbe Lage mit EINEM Recht - die Gruppe Rang und Beruf kommt dazu. */
    private static final List<String> NUR_RANG = List.of(
                "&#c6a15bAlle Befehle des Freimarkenbundes",
                        "&#edf1f3Grund und Bauen",
                        "&#e3c57e/parzelle&#6e7a82 - &#a5b4bfParzellen nehmen, ansehen und "
                                + "verwalten",
                        "&#edf1f3Geld und Handel",
                        "&#e3c57e/mark&#6e7a82 - &#a5b4bfDein Guthaben in Mark",
                        "&#edf1f3Rang und Beruf",
                        "&#e3c57e/rang&#6e7a82 - &#a5b4bfDein Rang, die naechste Stufe und die "
                                + "ganze Leiter",
                        "&#edf1f3Der Bund",
                        "&#e3c57e/freimarken&#6e7a82 - &#a5b4bfStand, Preise und Welt des "
                                + "Freimarkenbunds",
                        // /melden und /bannliste stehen AUCH hier: Beide Eintraege
                        // tragen JEDER, und das gilt unabhaengig von jedem Recht -
                        // genau wie /parzelle, /mark und /freimarken darueber. Plan
                        // 12c.4 macht die Bannliste ausdruecklich oeffentlich.
                        "&#e3c57e/melden&#6e7a82 - &#a5b4bfEinen Spieler melden - mit Grund, ein Moderator sieht es sich an",
                        "&#e3c57e/bannliste&#6e7a82 - &#a5b4bfWer gesperrt ist, warum und bis wann",
                        "&#6e7a82Jeder Befehl ohne Zusatz zeigt, was er kann.");

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    private HilfeBefehle befehle() {
        return new HilfeBefehle(Sprachprobe.spracheAusDemJar());
    }

    private List<String> zeilen(String... rechte) {
        Set<String> hat = Set.of(rechte);
        return befehle().zeilen(hat::contains).stream().map(Sprachprobe::bild).toList();
    }

    @Test
    @DisplayName("1 - mit allen Rechten steht jeder Befehl in seiner Gruppe")
    void mitAllenRechtenStehtJederBefehlDa() {
        var rechte = new java.util.TreeSet<String>();
        for (Hilfeplan.Eintrag eintrag : Hilfeplan.ALLE.eintraege()) {
            if (!eintrag.fuerJeden()) {
                rechte.add(eintrag.recht());
            }
        }

        assertEquals(ALLES, zeilen(rechte.toArray(new String[0])));
    }

    @Test
    @DisplayName("2 - ohne jedes Recht bleiben die drei Befehle, die jedem gehoeren")
    void ohneRechteBleibenDreiBefehle() {
        // UND DIE LEEREN GRUPPEN VERSCHWINDEN MIT, Ueberschrift und allem. Eine
        // Ueberschrift ohne eine Zeile darunter sieht aus wie ein Fehler, und ein
        // Spieler, der "Reisen und Treffen" ohne Inhalt liest, sucht den Rest.
        assertEquals(OHNE_RECHTE, zeilen());
    }

    @Test
    @DisplayName("3 - ein einziges Recht holt genau eine Gruppe und eine Zeile dazu")
    void einRechtHoltEineGruppeDazu() {
        assertEquals(NUR_RANG, zeilen(RangBefehle.RECHT));
    }

    @Test
    @DisplayName("4 - getippt kommt dieselbe Liste beim Spieler an")
    void getipptKommtDieselbeListeAn() throws CommandSyntaxException {
        // DER WEG DURCH BRIGADIER, und er prueft etwas, das der unmittelbare Aufruf
        // nicht kann: dass der Zweig ueberhaupt erreichbar ist und dass die Rechtefrage
        // am Spieler haengt und nicht an einer leeren Menge.
        var dora = new Spielerprobe("Dora", DORA, HilfeBefehle.RECHT,
                RangBefehle.RECHT);
        var baum = new Befehlsprobe(List.of(befehle().hilfe()));

        assertEquals(1, baum.tippe("hilfe", dora));

        assertEquals(zeilen(HilfeBefehle.RECHT, RangBefehle.RECHT), dora.chatBild());
    }

    @Test
    @DisplayName("5 - ohne das Recht gibt es den Befehl nicht")
    void ohneRechtGibtEsDenBefehlNicht() {
        var dora = new Spielerprobe("Dora", DORA);
        var baum = new Befehlsprobe(List.of(befehle().hilfe()));

        assertThrows(CommandSyntaxException.class, () -> baum.tippe("hilfe", dora));
    }

    @Test
    @DisplayName("6 - die Konsole bekommt die ganze Liste")
    void konsoleBekommtDieGanzeListe() throws CommandSyntaxException {
        // Die Konsole darf alles, und das ist richtig: Der Betreiber soll sehen, was
        // seine Spieler sehen koennen. Eine eigene Absage braucht /hilfe nicht - eine
        // Liste von Befehlen liest sich auch im Serverprotokoll.
        var baum = new Befehlsprobe(List.of(befehle().hilfe()));

        assertEquals(1, baum.tippeAufDerKonsole("hilfe"));

        assertEquals(ALLES, baum.konsoleBild());
    }

    @Test
    @DisplayName("7 - die Liste ist so lang wie der Plan Eintraege und Gruppen hat")
    void dieListeIstSoLangWieDerPlan() {
        // EINE ZAHL, und sie ist bewusst nicht die ganze Zusicherung: Sie faengt den
        // Fall, in dem jemand eine Gruppe in den Plan setzt und die Tafel ALLES oben
        // mitzieht, ohne die Zeile zu lesen. Der Wortlaut steht dort, die RECHNUNG steht
        // hier - Kopf, Fuss, je Gruppe eine Ueberschrift, je Eintrag eine Zeile.
        int erwartet = 2 + Hilfeplan.ALLE.gruppen().size()
                + Hilfeplan.ALLE.eintraege().size();

        assertEquals(erwartet, ALLES.size());
        assertEquals(36, ALLES.size());
    }

    @Test
    @DisplayName("8 - ein leerer Fuss sendet keine leere Zeile")
    void leererFussSendetKeineLeereZeile() {
        // Der Fuss darf leer bleiben (Satz.LEER_ERLAUBT). Wer ihn leer laesst, soll
        // keine leere Zeile im Chat bekommen - die liest sich wie ein verschluckter Satz.
        var sprache = Sprachprobe.mitZeilen(Map.of(HilfeSatz.FUSS.pfad(), ""));

        List<String> zeilen = new HilfeBefehle(sprache).zeilen(recht -> false).stream()
                .map(Sprachprobe::bild).toList();

        assertEquals(OHNE_RECHTE.subList(0, OHNE_RECHTE.size() - 1), zeilen);
        assertTrue(sprache.leerGelassen(HilfeSatz.FUSS));
    }
}
