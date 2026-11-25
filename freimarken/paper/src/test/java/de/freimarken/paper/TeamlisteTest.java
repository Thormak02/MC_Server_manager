package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Teamwerte;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Der Leser der Teamrollen - was der Betreiber tippt, und was daraus wird. */
@DisplayName("Der Leser der Teamrollen")
class TeamlisteTest {

    private static Teamliste.Ergebnis lies(String yaml) {
        return Teamliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));
    }

    @Test
    @DisplayName("1 - vollstaendige Rollen kommen vollstaendig an")
    void vollstaendig() {
        var ergebnis = lies("""
                team:
                  rollen:
                    helfer:
                      rechte:
                        - tpa
                    moderator:
                      rechte:
                        - tpa
                        - blockbuch
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        // Die Reihenfolge der Datei bleibt, obwohl sie nichts BEDEUTET: Sie steht im
        // Startbericht und in /team rollen, und eine Liste, die bei jedem Start anders
        // sortiert ist, laesst sich zwischen zwei Starts nicht vergleichen.
        assertEquals(List.of("helfer", "moderator"), ergebnis.werte().schluessel());
        assertEquals(Set.of("freimarken.tpa", "freimarken.blockbuch"),
                ergebnis.werte().rechteFuer(List.of("moderator")));
    }

    @Test
    @DisplayName("2 - ein FEHLENDER Abschnitt gibt die Vorgabe des Programms")
    void fehlenderAbschnitt() {
        var ergebnis = lies("wirtschaft:\n  startguthaben: 50000\n");

        // Das ist der Server, auf dem noch niemand an die Datei gedacht hat - er soll
        // seine fuenf Rollen haben.
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(Teamwerte.VORGABE.schluessel(), ergebnis.werte().schluessel());
    }

    @Test
    @DisplayName("3 - die Ungereimtheiten stehen AUCH bei fehlendem Abschnitt im Bericht")
    void ungereimtheitenAuchBeiderVorgabe() {
        var ergebnis = lies("wirtschaft:\n  startguthaben: 50000\n");

        // GEMESSEN am 25.11.2026 am Startbericht des Probeservers: Der Bericht entstand
        // vorher erst im letzten Zweig des Lesers. Ein Server ohne team:-Abschnitt bekam
        // damit die Vorgabe OHNE ihre Meldung - und das ist genau der Server, dem die
        // Auskunft am meisten hilft. Dieselbe Klasse Fehler wie bei einem Werkswert, der
        // durch andere Riegel laeuft als ein eingetragener: Rot wird dabei nichts, die
        // Zeile fehlt einfach.
        //
        // DIESE PROBE KANN DEN UMBAU NICHT MEHR HALTEN, und das ist ehrlich
        // hinzuschreiben: Bis Teil C hatte die Vorgabe eine Ungereimtheit (Helfer und
        // Eventleiter mit gleicher Rechteliste), und daran liess sich der Weg messen.
        // Seit der Helfer freimarken.meldungen hat, ist die Vorgabe sauber - die Probe
        // waere jetzt gruen MIT und OHNE fertig(). Gehalten wird der Weg darum am
        // Quelltext, in jederRueckwegGehtDurchFertig().
        assertEquals(List.of(), ergebnis.bericht(),
                "die Vorgabe ist seit Teil C ohne Ungereimtheit: " + ergebnis.bericht());
        assertEquals(Teamwerte.VORGABE.schluessel(), ergebnis.werte().schluessel());
    }

    @Test
    @DisplayName("3b - JEDER Rueckweg des Lesers geht durch fertig()")
    void jederRueckwegGehtDurchFertig() {
        // Die Zusage, die seit Teil C keine Verhaltensprobe mehr tragen kann - siehe
        // Probe 3. Geprueft wird am QUELLTEXT: Jedes return in ausKonfiguration muss
        // fertig(...) rufen, denn dort entsteht der Bericht. Ein frueher Rueckweg, der
        // unmittelbar ein new Ergebnis(...) baut, ueberspringt ihn - und meldet die
        // Ungereimtheiten der Werte nicht, die er zurueckgibt.
        //
        // Dieselbe Bauart wie die Quelltextzusagen in GrenzeTest, und aus demselben
        // Grund: Was kein Verhalten mehr zeigt, zeigt noch der Text.
        String quelle = quelltext();
        int anfang = quelle.indexOf("static Ergebnis ausKonfiguration(");
        assertTrue(anfang > 0, "ausKonfiguration nicht gefunden");
        int ende = quelle.indexOf("\n    /**", anfang);
        assertTrue(ende > anfang, "Ende der Methode nicht gefunden");
        String methode = quelle.substring(anfang, ende);

        var rueckwege = new java.util.ArrayList<String>();
        var muster = java.util.regex.Pattern.compile("return ([^;]+);");
        var suche = muster.matcher(methode);
        while (suche.find()) {
            rueckwege.add(suche.group(1).trim());
        }

        // FUENF und nicht vier - der fuenfte ist der im catch: Wirft der Konstruktor von
        // Teamwerte (heute nur bei zu vielen Rollen), gilt die Vorgabe, und auch DIESER
        // Weg muss den Bericht nennen. Beim ersten Schreiben dieser Probe war er
        // vergessen, und genau das ist der Fehler, den sie faengt.
        assertEquals(5, rueckwege.size(),
                "fuenf Rueckwege erwartet (team: fehlt, rollen: leer, rollen:"
                        + " unerwartet, gelesen, Wurf im catch) - gefunden: "
                        + rueckwege);
        for (String rueckweg : rueckwege) {
            assertTrue(rueckweg.startsWith("fertig("),
                    "Dieser Rueckweg ueberspringt den Bericht: return " + rueckweg);
        }
    }

    /** Der eigene Quelltext - gelesen wie in GrenzeTest, ohne Abschrift. */
    private static String quelltext() {
        java.nio.file.Path hier = java.nio.file.Path.of("").toAbsolutePath();
        for (java.nio.file.Path kandidat = hier; kandidat != null;
                kandidat = kandidat.getParent()) {
            for (java.nio.file.Path versuch : new java.nio.file.Path[] {
                    kandidat.resolve("src/main/java/de/freimarken/paper"),
                    kandidat.resolve("paper/src/main/java/de/freimarken/paper")}) {
                java.nio.file.Path datei = versuch.resolve("Teamliste.java");
                if (java.nio.file.Files.isRegularFile(datei)) {
                    try {
                        return java.nio.file.Files.readString(datei);
                    } catch (java.io.IOException fehler) {
                        throw new AssertionError("Teamliste.java nicht lesbar: " + datei,
                                fehler);
                    }
                }
            }
        }
        throw new AssertionError("Teamliste.java nicht gefunden - von " + hier);
    }

    @Test
    @DisplayName("4 - ein LEERER Abschnitt heisst: kein Team")
    void leererAbschnitt() {
        var ergebnis = lies("team:\n  rollen: {}\n");

        // Der Unterschied zum fehlenden Abschnitt ist die ganze Entscheidung: Hier hat
        // sich der Betreiber entschieden. Wer beides gleich behandelte, gaebe ihm fuenf
        // Rollen zurueck, die er ausdruecklich abgeschafft hat.
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertFalse(ergebnis.werte().greift());
        assertEquals(List.of(), ergebnis.werte().schluessel());
    }

    @Test
    @DisplayName("5 - ein leerer Schluessel ohne Inhalt heisst ebenfalls: kein Team")
    void schluesselOhneInhalt() {
        // "rollen:" und darunter nichts. GEMESSEN am 25.11.2026: Bukkit gibt fuer einen
        // Schluessel ohne Wert dasselbe zurueck wie fuer einen fehlenden - contains
        // ("team.rollen", true) ist hier schon false. Fragte der Leser danach, bekaeme
        // ein Betreiber, der seine Rollen ABGESCHAFFT hat, die fuenf des Programms
        // zurueck. Darum fragt er den Elternknoten team:, und der ist hier da.
        var ergebnis = lies("team:\n  rollen:\n");

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertFalse(ergebnis.werte().greift());
    }

    @Test
    @DisplayName("5b - ein team:-Abschnitt OHNE rollen-Zeile heisst auch: kein Team")
    void abschnittOhneRollenzeile() {
        // Die Gegenprobe zu 5: Hier steht team: mit einem anderen Schluessel darunter.
        // Dieselbe Antwort, und aus demselben Grund - wer den Abschnitt in seiner Datei
        // hat, hat sich mit den Rollen befasst. Die Probe steht hier, weil der Unterschied
        // zu Probe 2 (team: fehlt GANZ) genau an dieser Zeile haengt.
        var ergebnis = lies("team:\n  etwas-anderes: 1\n");

        assertFalse(ergebnis.werte().greift(),
                "mit team: in der Datei gilt die Vorgabe nicht mehr");
    }

    @Test
    @DisplayName("6 - eine Rolle mit der Vorsilbe im Recht faellt GANZ weg")
    void vorsilbeImRecht() {
        var ergebnis = lies("""
                team:
                  rollen:
                    gut:
                      rechte:
                        - tpa
                    schlecht:
                      rechte:
                        - freimarken.blockbuch
                """);

        // Die kaputte Rolle faellt weg, die gute bleibt. Eine Rolle, der ein Recht
        // fehlte, waere eine SCHWAECHERE - wer sie hat, duerfte weniger als eingestellt,
        // und das sieht im Spiel aus wie ein Fehler des Plugins.
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("freimarken."),
                "die Klage nennt die Vorsilbe: " + ergebnis.klagen().get(0));
        assertEquals(List.of("gut"), ergebnis.werte().schluessel());
    }

    @Test
    @DisplayName("7 - eine Rolle ohne rechte-Zeile ist erlaubt und steht im Bericht")
    void rolleOhneRechte() {
        var ergebnis = lies("""
                team:
                  rollen:
                    vorbereitet:
                      rechte: []
                """);

        // Erlaubt, weil ein Betreiber eine Rolle vorbereiten darf, deren Werkzeuge noch
        // nicht gebaut sind. Gemeldet gehoert sie trotzdem: Vergeben laesst sie sich, und
        // wer sie bekommt, darf danach genau so viel wie vorher.
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(List.of("vorbereitet"), ergebnis.werte().schluessel());
        assertEquals(1, ergebnis.bericht().size(), ergebnis.bericht().toString());
        assertTrue(ergebnis.bericht().get(0).contains("kein einziges Recht"),
                ergebnis.bericht().get(0));
    }

    @Test
    @DisplayName("8 - eine Rolle, die kein Abschnitt ist, faellt weg")
    void rolleIstKeinAbschnitt() {
        var ergebnis = lies("""
                team:
                  rollen:
                    gut:
                      rechte:
                        - tpa
                    schlecht: ja
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains(Teamwerte.SCHLUESSEL_RECHTE),
                "die Klage sagt, welche Zeile fehlt: " + ergebnis.klagen().get(0));
        assertEquals(List.of("gut"), ergebnis.werte().schluessel());
    }

    @Test
    @DisplayName("9 - eine rechte-Zeile, die keine Liste ist, laesst die Rolle wegfallen")
    void rechteSindKeineListe() {
        var ergebnis = lies("""
                team:
                  rollen:
                    schlecht:
                      rechte: tpa
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("Liste"), ergebnis.klagen().get(0));
        // Keine brauchbare Rolle: Dann hat dieser Server kein Team - und der Bericht
        // sagt, dass das Vergebene stehen bleibt.
        assertFalse(ergebnis.werte().greift());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("bleibt in der Datenbank")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("10 - ein grossgeschriebener Rollenname wird kleingeschrieben gelesen")
    void grossgeschriebenerName() {
        var ergebnis = lies("""
                team:
                  rollen:
                    Moderator:
                      rechte:
                        - tpa
                """);

        // Der LESER normalisiert, der Record nicht - daran haengt der Satzpfad
        // team.rolle.<name>, und ein Pfad wird woertlich nachgeschlagen.
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(List.of("moderator"), ergebnis.werte().schluessel());
    }

    @Test
    @DisplayName("11 - ein Recht, das es nicht gibt, wird gemeldet")
    void unbekanntesRecht() {
        Teamwerte werte = new Teamwerte(List.of(
                new Teamwerte.Rolle("eine", List.of("blockbuchh", "tpa"))));

        List<String> unbekannt = Teamliste.unbekannteRechte(werte,
                Set.of("freimarken.tpa", "freimarken.blockbuch"));

        // Ein vertipptes blockbuchh faellt sonst nirgends auf: Das Anhaengsel setzt jeden
        // Namen klaglos, und es fragt es nie jemand.
        assertEquals(1, unbekannt.size(), unbekannt.toString());
        assertTrue(unbekannt.get(0).contains("freimarken.blockbuchh"),
                unbekannt.get(0));
        assertTrue(unbekannt.get(0).contains("eine"),
                "und nennt die Rolle: " + unbekannt.get(0));
    }

    @Test
    @DisplayName("12 - jedes Recht der VORGABE ist ein Recht, das es gibt")
    void vorgabeNenntNurEchteRechte() {
        // Die Gegenprobe zur Probe darueber, und sie haelt die Vorgabe selbst: Ein
        // Rechtename, der in plugin.yml nicht angemeldet ist, stuende beim Start als
        // Fehler da - zu Recht, denn er wirkte nicht. Angemeldet sind sie hier als
        // Menge, weil nur ein laufender Server die echte Liste hat; dass sie mit der
        // plugin.yml zusammenpasst, haelt GrenzeTest 31cy.
        Set<String> angemeldet = Set.of("freimarken.tpa", "freimarken.sofort",
                "freimarken.blockbuch", "freimarken.spielzeit.andere",
                "freimarken.verwalten", "freimarken.besitz",
                "freimarken.freistadt.raeumen", "freimarken.team",
                "freimarken.team.vergeben", "freimarken.bauen.ueberall",
                // Schritt 16a Teil C: Helfer, Moderator und Verwalter bearbeiten
                // Meldungen. Das ist die Trennung, die den Helfer seit Teil C vom
                // Eventleiter unterscheidet.
                "freimarken.meldungen",
                // Schritt 16a Teil B: die Strafen. Die ersten vier hat der
                // Moderator, die letzten zwei nur der Verwalter - das ist die
                // Trennung aus Plan 12c.2 zwischen zeitlichem und dauerhaftem Bann,
                // und sie ist der Inhalt dieses Teils.
                "freimarken.strafen.stumm", "freimarken.strafen.rauswurf",
                "freimarken.strafen.bann", "freimarken.strafen.aufheben",
                "freimarken.strafen.dauerhaft", "freimarken.strafen.team",
                // Schritt 16a Teil D: das Einsehen. Moderator UND Verwalter
                // tragen es - Plan 12c.2 nennt das Einsehen von Grundstuecken
                // als Moderationswerkzeug und nicht als Verwaltungssache.
                "freimarken.einsehen",
                // 25.11.2026: /portal. Nur der Verwalter traegt es - ein neues
                // Tor ist ein neuer Weg in eine Welt, auch in eine mit
                // Rangschranke, und das ist keine Moderation.
                "freimarken.portal");

        assertEquals(List.of(),
                Teamliste.unbekannteRechte(Teamwerte.VORGABE, angemeldet),
                "die Vorgabe darf kein Recht nennen, das es nicht gibt");
    }

    @Test
    @DisplayName("13 - die Rechenprobe nennt jede Rolle mit der Zahl ihrer Rechte")
    void rechenprobe() {
        Teamwerte werte = new Teamwerte(List.of(
                new Teamwerte.Rolle("helfer", List.of("tpa")),
                new Teamwerte.Rolle("moderator", List.of("tpa", "blockbuch"))));

        String zeile = Teamliste.rechenprobe(werte);

        // Die ZAHL und nicht die Namen: Eine Rolle hat bis zu einem Dutzend Rechte, und
        // eine Zeile, die alle nennt, liest niemand mehr. Die Zahl genuegt fuer die
        // Frage, die ein Betreiber beim Start wirklich hat - ist meine Datei angekommen?
        assertTrue(zeile.contains("helfer (1)"), zeile);
        assertTrue(zeile.contains("moderator (2)"), zeile);
    }

    @Test
    @DisplayName("14 - die Rechenprobe sagt es, wenn es kein Team gibt")
    void rechenprobeOhneTeam() {
        String zeile = Teamliste.rechenprobe(new Teamwerte(List.of()));

        // Und sie sagt BEIDES: dass nichts wirkt und dass nichts verloren ist. Sonst
        // loescht ein Betreiber im guten Glauben seine Datenbankzeilen.
        assertTrue(zeile.contains("kein Team"), zeile);
        assertTrue(zeile.contains("bleibt aber stehen"), zeile);
    }

    @Test
    @DisplayName("15 - ohne Konfiguration und ohne Werte wird nichts gemeldet")
    void fehlendeAngaben() {
        // Kein Wurf: Dieser Leser laeuft beim Start, und ein Wurf dort ist ein Server,
        // der nicht hochkommt.
        var ergebnis = Teamliste.ausKonfiguration(null);
        assertEquals(Teamwerte.VORGABE.schluessel(), ergebnis.werte().schluessel());
        assertEquals(List.of(), Teamliste.unbekannteRechte(null, Set.of()));
        assertEquals(List.of(), Teamliste.unbekannteRechte(Teamwerte.VORGABE, null));
        assertTrue(Teamliste.rechenprobe(null).contains("keine"));
    }
}
