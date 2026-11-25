package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Hilfeplan gegen den Rest des Plugins - ohne Server, ohne Sprache.
 *
 * <p><b>Was hier geprueft wird, kann keine Bildtafel sehen:</b> ob die LISTE vollstaendig
 * ist. Ein Befehl, den niemand in den Plan eingetragen hat, laeuft im Spiel tadellos -
 * und ein Neuling erfaehrt nie, dass es ihn gibt. Darum wird der Plan gegen zwei andere
 * Stellen gehalten, in beide Richtungen: gegen die Befehlsbeschreibungen von
 * {@link BundSatz} (jeder angemeldete Befehl hat genau eine) und gegen den Quelltext der
 * Anmeldung in {@code FreimarkenPlugin}.
 */
@DisplayName("Der Hilfeplan")
class HilfeplanTest {

    /** {@code befehl.beschreibung.} - der Pfad vor dem Befehlsnamen. */
    private static final String VORSILBE = "befehl.beschreibung.";

    @Test
    @DisplayName("1 - jeder beschriebene Befehl steht im Plan, und kein anderer")
    void jederBeschriebeneBefehlStehtImPlan() {
        // BEIDE RICHTUNGEN, und jede faengt einen anderen Fehler:
        //   - ein Befehl OHNE Eintrag: Es gibt ihn, aber /hilfe nennt ihn nicht - und
        //     genau dafuer ist diese Liste da.
        //   - ein Eintrag OHNE Befehl: /hilfe nennt einen Befehl, den es nicht gibt, und
        //     wer ihn tippt, liest Brigadiers englisches "unknown command".
        var beschrieben = new TreeSet<String>();
        for (Satz satz : BundSatz.beschreibungen()) {
            beschrieben.add(satz.pfad().substring(VORSILBE.length()));
        }
        var imPlan = new TreeSet<>(Hilfeplan.ALLE.befehle());

        assertEquals(beschrieben, imPlan, "Links die Befehle, die eine Beschreibung"
                + " haben (also angemeldet sind), rechts die, die /hilfe zeigt.");
    }

    @Test
    @DisplayName("2 - jeder Eintrag zeigt auf die Beschreibung SEINES Befehls")
    void jederEintragZeigtAufSeineBeschreibung() {
        // OHNE DIESE PROBE waere die erste gruen, obwohl /rang die Beschreibung von
        // /markt traegt: Beide Mengen stimmen dann, nur die Paarung ist vertauscht - und
        // im Spiel liest ein Neuling neben /rang, was der Bundesmarkt ankauft.
        List<String> falschGepaart = new java.util.ArrayList<>();
        for (Hilfeplan.Eintrag eintrag : Hilfeplan.ALLE.eintraege()) {
            String erwartet = VORSILBE + eintrag.befehl();
            if (!erwartet.equals(eintrag.beschreibung().pfad())) {
                falschGepaart.add("/" + eintrag.befehl() + " traegt "
                        + eintrag.beschreibung().pfad());
            }
        }
        assertEquals(List.of(), falschGepaart);
    }

    @Test
    @DisplayName("3 - jeder Befehl des Hauptmenues steht im Plan")
    void jederMenuebefehlStehtImPlan() {
        // Das Menue zeigt elf Befehle, und ein Klick fuehrt sie aus. Steht einer nicht im
        // Plan, nennt /hilfe ihn nicht - und das Menue wuerde die einzige Stelle, an der
        // ein Spieler ueberhaupt von ihm erfaehrt.
        var imPlan = new TreeSet<>(Hilfeplan.ALLE.befehle());
        var fehlend = new TreeSet<String>();
        for (String befehl : Menuetafel.HAUPT.befehle()) {
            if (!imPlan.contains(befehl)) {
                fehlend.add(befehl);
            }
        }
        assertEquals(new TreeSet<String>(), fehlend);
    }

    @Test
    @DisplayName("4 - jede Beschreibung wird in der Anmeldung wirklich benutzt")
    void jedeBeschreibungWirdAngemeldet() {
        // DIE EINZIGE PROBE, die den Plan an die WIRKLICHKEIT bindet. Die drei davor
        // halten drei Listen aneinander - stuende ein Befehl in allen dreien und waere
        // nirgends angemeldet, bliebe jede gruen. Hier wird der Quelltext der Anmeldung
        // gelesen: Jede Beschreibung muss dort in einem register-Aufruf vorkommen.
        String anmeldung = Quellen.ohneKommentare(
                Quellen.text(Path.of(Quellen.ordner().toString(),
                        "FreimarkenPlugin.java")));
        var fehlend = new TreeSet<String>();
        for (Satz satz : BundSatz.beschreibungen()) {
            String marke = "BundSatz." + ((Enum<?>) satz).name();
            if (!anmeldung.contains(marke)) {
                fehlend.add(satz.pfad());
            }
        }
        assertEquals(new TreeSet<String>(), fehlend, "Eine Beschreibung, die in keiner"
                + " Anmeldung steht, gehoert zu einem Befehl, den es nicht gibt - und"
                + " /hilfe nennt ihn trotzdem.");
    }

    @Test
    @DisplayName("5 - derselbe Befehl in zwei Gruppen wird abgewiesen")
    void derselbeBefehlZweimalWirdAbgewiesen() {
        var eintrag = new Hilfeplan.Eintrag("mark", Hilfeplan.JEDER,
                BundSatz.BEFEHL_MARK);
        var eine = new Hilfeplan.Gruppe(HilfeSatz.GRUPPE_GELD, List.of(eintrag));
        var andere = new Hilfeplan.Gruppe(HilfeSatz.GRUPPE_BUND, List.of(eintrag));

        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Hilfeplan(List.of(eine, andere)));

        assertTrue(fehler.getMessage().contains("/mark"), fehler.getMessage());
    }

    @Test
    @DisplayName("6 - eine Gruppe ohne Eintrag wird abgewiesen")
    void gruppeOhneEintragWirdAbgewiesen() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Hilfeplan.Gruppe(HilfeSatz.GRUPPE_GELD, List.of()));

        assertTrue(fehler.getMessage().contains("hilfe.gruppe.geld"),
                fehler.getMessage());
    }

    @Test
    @DisplayName("7 - ein Befehl MIT Schraegstrich wird abgewiesen")
    void befehlMitSchraegstrichWirdAbgewiesen() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Hilfeplan.Eintrag("/mark", Hilfeplan.JEDER,
                        BundSatz.BEFEHL_MARK));

        assertTrue(fehler.getMessage().contains("zweimal"), fehler.getMessage());
    }

    @Test
    @DisplayName("8 - eine Beschreibung, die kein WORT ist, wird abgewiesen")
    void beschreibungOhneWortWirdAbgewiesen() {
        // BundSatz.STAND_KOPF ist Art.SATZ und traegt ein Farbtag. Eingesetzt stuende es
        // dem Spieler woertlich in der Zeile - und Sprache.wort wirft.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Hilfeplan.Eintrag("mark", Hilfeplan.JEDER,
                        BundSatz.STAND_KOPF));

        assertTrue(fehler.getMessage().contains("WORT"), fehler.getMessage());
    }

    @Test
    @DisplayName("10 - jeder Eintrag traegt das Recht SEINES Befehls")
    void jederEintragTraegtSeinRecht() {
        // DIE ZWANZIG PAARE WOERTLICH [21.11.2026], und nicht aus dem Plan abgeleitet.
        // Bis heute wurde eintrag.recht() im ganzen Probenbaum genau einmal gelesen -
        // und dort baute die Probe ihre Rechtemenge AUS dem Plan, den sie prueft. Ein
        // vertauschtes Recht war damit unsichtbar: Im Spiel steht dann ein Befehl in der
        // Liste eines Spielers, der ihn nicht tippen darf (Brigadiers englisches
        // "Unknown or incomplete command"), und der, den er darf, fehlt.
        //
        // Hilfeplan sagt es selbst zu: "Eine Liste, die einen Befehl anbietet, den der
        // Spieler dann nicht ausfuehren darf, waere schlimmer als keine Liste."
        Map<String, String> gebaut = new LinkedHashMap<>();
        for (Hilfeplan.Eintrag eintrag : Hilfeplan.ALLE.eintraege()) {
            gebaut.put(eintrag.befehl(), eintrag.recht());
        }

        assertEquals(new LinkedHashMap<>(Map.ofEntries(
                        Map.entry("parzelle", Hilfeplan.JEDER),
                        Map.entry("laden", LadenBefehle.RECHT),
                        Map.entry("mark", Hilfeplan.JEDER),
                        Map.entry("markt", MarktBefehle.RECHT),
                        Map.entry("shop", ShopBefehle.RECHT),
                        Map.entry("rang", RangBefehle.RECHT),
                        Map.entry("beruf", BerufeBefehle.RECHT),
                        Map.entry("warp", WarpBefehle.RECHT_REISEN),
                        Map.entry("zurueck", RueckwegBefehle.RECHT_ZURUECK),
                        Map.entry("tpa", AnfragenBefehle.RECHT_TPA),
                        Map.entry("tpahere", AnfragenBefehle.RECHT_TPA),
                        Map.entry("tpaccept", AnfragenBefehle.RECHT_TPA),
                        Map.entry("tpdeny", AnfragenBefehle.RECHT_TPA),
                        Map.entry("freistadt", FreistadtBefehle.RECHT),
                        Map.entry("werder", WerderBefehle.RECHT),
                        Map.entry("wolke", WolkeBefehle.RECHT),
                        Map.entry("menu", MenueBefehle.RECHT),
                        Map.entry("hilfe", HilfeBefehle.RECHT),
                        Map.entry("regeln", RegelnBefehle.RECHT),
                        Map.entry("lotterie", LotterieBefehle.RECHT),
                        Map.entry("wettbewerb", WettbewerbBefehle.RECHT),
                        Map.entry("freimarken", Hilfeplan.JEDER),
                        // Schritt 16a Teil A: /team traegt das LESE-Recht und
                        // nicht freimarken.team.vergeben. Der Eintrag entscheidet,
                        // wer die ZEILE sieht, und sehen darf sie, wer das Team
                        // ansehen darf - vergeben ist ein zweiter Riegel am Zweig.
                        Map.entry("team", TeamBefehle.RECHT),
                        // /melden traegt JEDER und nicht MeldeBefehle.RECHT -
                        // siehe die Begruendung am Eintrag selbst: Die Zeile soll
                        // auch dann stehen, wenn ein Betreiber das Recht umhaengt.
                        Map.entry("melden", Hilfeplan.JEDER),
                        Map.entry("meldung", MeldungBefehle.RECHT),
                        // Schritt 16a Teil B: /strafe traegt das Recht fuer die
                        // SPERRE - das schwerste der drei Artrechte. Ein Eintrag
                        // kann genau ein Recht nennen, und wer bannen darf, darf
                        // auch stummschalten und hinauswerfen.
                        Map.entry("strafe", StrafBefehle.RECHTE.bann()),
                        // /bannliste traegt JEDER, wie /melden - Plan 12c.4 macht
                        // sie ausdruecklich oeffentlich. Das Recht
                        // freimarken.bannliste steht auf default true; ob die Liste
                        // wirklich jedem offen ist, entscheidet zusaetzlich
                        // moderation.strafen.bannliste-oeffentlich.
                        Map.entry("bannliste", Hilfeplan.JEDER))),
                gebaut, "Ein Eintrag traegt ein anderes Recht als sein Befehl.");
    }

    @Test
    @DisplayName("11 - das Recht steht so auch im requires des Befehls")
    void dasRechtStehtAuchImBefehl() {
        // DIE ANDERE SEITE, aus dem Quelltext [21.11.2026]. Probe 10 schreibt die Paare
        // auf, aber beide Zeilen stehen im Probenbaum - wer den Plan aendert, koennte
        // die Map mitaendern. Diese Tafel liest die Klasse, die das Recht WIRKLICH
        // fragt: Steht die Konstante nicht im requires ihrer Wurzel, zeigt die Liste
        // nach einem anderen Recht als der Befehl verlangt.
        //
        // Geprueft wird der NAME der Konstante im Quelltext und nicht ihr Wert: Zwei
        // Konstanten derselben Klasse (LadenBefehle.RECHT und .RECHT_MIETEN) haben
        // verschiedene Werte, und genau ihr Verwechseln ist der naheliegende Griff.
        Map<String, String> wurzelrecht = Map.ofEntries(
                Map.entry("LadenBefehle.java", "RECHT"),
                Map.entry("MarktBefehle.java", "RECHT"),
                Map.entry("ShopBefehle.java", "RECHT"),
                Map.entry("RangBefehle.java", "RECHT"),
                Map.entry("BerufeBefehle.java", "RECHT"),
                Map.entry("WarpBefehle.java", "RECHT_REISEN"),
                Map.entry("RueckwegBefehle.java", "RECHT_ZURUECK"),
                Map.entry("AnfragenBefehle.java", "RECHT_TPA"),
                Map.entry("FreistadtBefehle.java", "RECHT"),
                Map.entry("WerderBefehle.java", "RECHT"),
                Map.entry("WolkeBefehle.java", "RECHT"),
                Map.entry("MenueBefehle.java", "RECHT"),
                Map.entry("HilfeBefehle.java", "RECHT"),
                Map.entry("RegelnBefehle.java", "RECHT"),
                // Schritt 16a Teil B: /strafe fragt die Artrechte nicht einzeln an
                // der Wurzel, sondern ueber darfUeberhaupt - dort steht
                // RECHTE.fuer(art) in einer Schleife ueber Strafart.values(). Der
                // Name, der im Quelltext steht, ist darum RECHTE.aufheben() als
                // letzter Fang. Geprueft wird die GESTALT des Aufrufs; eine Zeile,
                // die kein hasPermission mehr enthaelt, ist ein Befehl ohne Riegel.
                Map.entry("StrafBefehle.java", "RECHT_BANNLISTE"));

        List<String> fehlt = new ArrayList<>();
        for (Map.Entry<String, String> paar : new TreeMap<>(wurzelrecht).entrySet()) {
            String quelle = Quellen.text(Quellen.alle().stream()
                    .filter(datei -> datei.getFileName().toString().equals(paar.getKey()))
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "die Datei " + paar.getKey() + " gibt es nicht mehr")));
            if (!quelle.contains("hasPermission(" + paar.getValue() + ")")) {
                fehlt.add(paar.getKey() + " fragt nicht hasPermission("
                        + paar.getValue() + ")");
            }
        }
        assertEquals(List.of(), fehlt, "Der Hilfeplan nennt fuer diesen Befehl ein Recht,"
                + " das seine Wurzel nicht fragt - die Liste zeigt ihn dann dem"
                + " Falschen.");
    }

    @Test
    @DisplayName("9 - fuerJeden gilt genau fuer die drei Befehle ohne eigenes Recht")
    void fuerJedenGiltFuerDreiBefehle() {
        // DIE DREI SIND NACHGESEHEN, nicht angenommen: /parzelle und /mark fragen an der
        // Wurzel nur, ob dort ein Spieler steht, und /freimarken hat seine Rechte an den
        // Verwaltungszweigen. Kommt ein vierter dazu, ist diese Probe rot - und wer sie
        // anpasst, muss im requires jenes Befehls nachsehen, ob das stimmt.
        var ohneRecht = new TreeSet<String>();
        for (Hilfeplan.Eintrag eintrag : Hilfeplan.ALLE.eintraege()) {
            if (eintrag.fuerJeden()) {
                ohneRecht.add(eintrag.befehl());
            }
        }
        // VIER seit Teil C: /melden gehoert jedem, und zwar notwendig - wer einen
        // Vorfall sieht, soll den Befehl in seiner Liste finden, ohne jemanden zu
        // fragen. Die Ueberschrift sagt "drei" und ist damit ueberholt; was die Probe
        // haelt, ist der Vergleich hier.
        assertEquals(new TreeSet<>(List.of("bannliste", "freimarken", "mark", "melden", "parzelle")),
                ohneRecht);
    }
}
