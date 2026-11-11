package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Wirtschaftswerte;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

/**
 * Die ausgelieferte Datei gegen den Vertrag - in BEIDE Richtungen.
 *
 * <p>Der Vertrag steht wortlos in Java ({@link Satz}), der Wortlaut nur in der Ressource.
 * Darum vergleicht diese Probe zwei UNABHAENGIGE Quellen und ist nicht selbstbezueglich:
 * Wer beim Abschreiben einem Satz seinen {@code {betrag}} wegnimmt, bekommt eine rote
 * Probe. Haette der Vertrag mit dem Text gewandert - also die Platzhaltermenge aus dem
 * ausgelieferten Satz gelesen -, waere genau diese Fehlerklasse unpruefbar.
 */
class VorlageTest {

    /** Jeder Pfad der ausgelieferten Vorlage auf seinen Wert, mit Zeilennummer. */
    private static Map<String, String> zeilenDerVorlage() {
        Map<String, String> gefunden = new java.util.LinkedHashMap<>();
        Node wurzel = new Yaml().compose(new StringReader(Sprachprobe.vorlagentext()));
        assertNotNull(wurzel, "die ausgelieferte Vorlage ist leer");
        sammle("", wurzel, gefunden);
        return gefunden;
    }

    private static void sammle(String pfad, Node knoten, Map<String, String> hinein) {
        if (knoten instanceof MappingNode abbildung) {
            for (NodeTuple paar : abbildung.getValue()) {
                ScalarNode name = (ScalarNode) paar.getKeyNode();
                String voll = pfad.isEmpty() ? name.getValue()
                        : pfad + "." + name.getValue();
                sammle(voll, paar.getValueNode(), hinein);
            }
            return;
        }
        hinein.put(pfad, knoten instanceof ScalarNode wert ? wert.getValue() : null);
    }

    @Test
    @DisplayName("1 - jeder Schluessel in Java hat eine Zeile in der Ressource")
    void jederSchluesselStehtInDerVorlage() {
        // assertNotNull auf die Ressource und nicht assumeTrue: Eine fehlende Vorlage ist
        // ein Bauunfall, und eine uebersprungene Probe faellt niemandem auf.
        assertNotNull(Sprachliste.class.getResourceAsStream("/" + Sprachliste.VORLAGE),
                Sprachliste.VORLAGE + " liegt nicht in der Jar");
        Map<String, String> vorlage = zeilenDerVorlage();
        List<String> fehlen = new ArrayList<>();
        for (Satz satz : Sprachschluessel.alle()) {
            for (String pfad : satz.pfade()) {
                if (!vorlage.containsKey(pfad)) {
                    fehlen.add(pfad);
                }
            }
        }
        assertEquals(List.of(), fehlen, "ohne Zeile in " + Sprachliste.VORLAGE);
        // Und derselbe Weg, den der Server geht: ausDemJar wirft bei jedem Mangel.
        assertDoesNotThrow(Sprachprobe::ausDemJar);
    }

    @Test
    @DisplayName("2 - keine Zeile in der Ressource ohne Schluessel in Java")
    void keineZeileOhneSchluessel() {
        List<String> ueberzaehlig = new ArrayList<>();
        for (String pfad : zeilenDerVorlage().keySet()) {
            if (Sprachschluessel.zuPfad(pfad) == null) {
                ueberzaehlig.add(pfad);
            }
        }
        assertEquals(List.of(), ueberzaehlig, "gehoert zu keinem Satz dieses Plugins -"
                + " beim Abschreiben vertippt, oder der Schluessel fehlt in Java");
    }

    @Test
    @DisplayName("3 - die Platzhalter der Vorlage und der Vertrag stimmen ueberein")
    void platzhalterStimmenMitDemVertrag() {
        Map<String, String> vorlage = zeilenDerVorlage();
        List<String> maengel = new ArrayList<>();
        for (Satz satz : Sprachschluessel.alle()) {
            Set<String> genannt = new TreeSet<>();
            for (String pfad : satz.pfade()) {
                String wert = vorlage.get(pfad);
                if (wert == null) {
                    continue;
                }
                Matcher treffer = Sprachliste.MARKE.matcher(wert);
                while (treffer.find()) {
                    genannt.add(treffer.group(1));
                    if (!satz.platzhalter().contains(treffer.group(1))) {
                        maengel.add(pfad + " nennt {" + treffer.group(1) + "}, der"
                                + " Vertrag nicht");
                    }
                }
            }
            for (String vertraglich : new TreeSet<>(satz.platzhalter())) {
                if (!genannt.contains(vertraglich)) {
                    // Das ist die Probe, die rot wird, wenn ein Satz beim Abschreiben
                    // seinen Betrag verliert.
                    maengel.add(satz.pfad() + ": der Vertrag bietet {" + vertraglich
                            + "}, keine Form nennt ihn");
                }
            }
        }
        assertEquals(List.of(), maengel);
    }

    @Test
    @DisplayName("4 - die Zeichenwache auf der ausgelieferten Datei selbst")
    void zeichenwacheAufDerVorlage() {
        List<String> klagen = new ArrayList<>();
        zeilenDerVorlage().forEach((pfad, wert) -> {
            if (wert == null) {
                klagen.add(pfad + " ist kein Text");
                return;
            }
            String klage = Sprachliste.zeichenklage(pfad, 0, wert);
            if (klage != null) {
                klagen.add(klage);
            }
        });
        assertEquals(List.of(), klagen);
        // Und die andere Haelfte derselben Regel: Der Trenner U+00B7 ist ausdruecklich
        // ERLAUBT. Gemessen kommt er 18-mal in FreimarkenPlugin und ParzellenBefehle vor,
        // also traegt ihn spaetestens der Bauschritt von /freimarken - und waere er
        // verboten, liesse sich kein einziger dieser Saetze mehr abschreiben. Solange
        // kein ausgelieferter Satz ihn traegt, sichert die Schleife darueber diese Haelfte
        // nicht zu.
        assertNull(Sprachliste.zeichenklage("probe.trenner", 0,
                        "<gray>/parzelle " + (char) 0xb7 + " /mark</gray>"),
                "der Trenner U+00B7 muss erlaubt bleiben");
        assertNotNull(Sprachliste.zeichenklage("probe.umlaut", 0,
                        "<gray>Gr" + (char) 0xfc + "ndung</gray>"),
                "ein Umlaut muss fallen - ae, oe, ue, ss schreiben");
    }

    @Test
    @DisplayName("5 - jedes Tag der Vorlage ist erlaubt und geschlossen")
    void jedesTagErlaubtUndGeschlossen() {
        Map<String, String> vorlage = zeilenDerVorlage();
        List<String> klagen = new ArrayList<>();
        for (Satz satz : Sprachschluessel.alle()) {
            if (satz.art() == Satz.Art.WORT || satz.art() == Satz.Art.MUSTER) {
                continue;
            }
            for (String pfad : satz.pfade()) {
                String klage = Sprachliste.tagklage(pfad, 0, vorlage.get(pfad));
                if (klage != null) {
                    klagen.add(klage);
                }
            }
        }
        assertEquals(List.of(), klagen);
    }

    @Test
    @DisplayName("6 - ein gezaehlter Satz hat beide Formen, und die Regel greift")
    void jederGezaehlteHatBeideFormen() {
        Map<String, String> vorlage = zeilenDerVorlage();
        List<String> maengel = new ArrayList<>();
        for (Satz satz : Sprachschluessel.alle()) {
            if (satz.art() != Satz.Art.GEZAEHLT) {
                continue;
            }
            for (String form : List.of(".eins", ".viele")) {
                if (!vorlage.containsKey(satz.pfad() + form)) {
                    maengel.add(satz.pfad() + form + " fehlt");
                }
            }
        }
        assertEquals(List.of(), maengel);
        // Die Regel selbst, an einem eigenen Schluessel: Solange kein ausgelieferter Satz
        // gezaehlt ist, haette die Schleife darueber nichts zugesichert - und eine gruene
        // Probe, die nichts geprueft hat, ist schlimmer als eine rote.
        Satz gezaehlt = Sprachprobe.Probesatz.von("probe.gezaehlt", Satz.Art.GEZAEHLT,
                "max");
        Sprachbuch vorbild = Sprachprobe.buch(List.of(gezaehlt),
                "probe.gezaehlt.eins", "<red>eine {max}</red>",
                "probe.gezaehlt.viele", "<red>viele {max}</red>");
        var halb = Sprachliste.ausQuelle("probe:\n  gezaehlt:\n    eins: \"<red>nur eine"
                + " {max}</red>\"\n", vorbild, List.of(gezaehlt));
        assertNull(halb.buch(), "eine halbe Mehrzahl darf nichts tauschen");
        assertTrue(halb.klagen().stream().anyMatch(k -> k.contains("probe.gezaehlt")
                        && k.contains("nur die Form eins")),
                "Klage erwartet, bekommen: " + halb.klagen());
    }

    @Test
    @DisplayName("7 - ein Wort und ein Muster tragen kein Farbtag")
    void wortUndMusterOhneTag() {
        Map<String, String> vorlage = zeilenDerVorlage();
        List<String> maengel = new ArrayList<>();
        for (Satz satz : Sprachschluessel.alle()) {
            if (satz.art() != Satz.Art.WORT && satz.art() != Satz.Art.MUSTER) {
                continue;
            }
            for (String pfad : satz.pfade()) {
                String wert = String.valueOf(vorlage.get(pfad));
                if (wert.indexOf('<') >= 0) {
                    // Ein eingesetzter Wert ist ein Blatt - ein Tag darin stuende dem
                    // Spieler woertlich im Chat.
                    maengel.add(pfad + " traegt eine spitze Klammer: " + wert);
                }
            }
        }
        assertEquals(List.of(), maengel);
        assertTrue(Sprachschluessel.alle().stream()
                        .anyMatch(satz -> satz.art() == Satz.Art.WORT),
                "ohne einen einzigen Art.WORT wuerde diese Probe nichts zusichern");
    }

    @Test
    @DisplayName("8 - jedes Zeitmuster der Vorlage uebersetzt ohne Wurf")
    void musterUebersetzenOhneWurf() {
        Sprachbuch buch = Sprachprobe.ausDemJar();
        int geprueft = 0;
        for (Satz satz : Sprachschluessel.alle()) {
            if (satz.art() != Satz.Art.MUSTER) {
                continue;
            }
            geprueft++;
            var form = buch.muster(satz);
            assertNotNull(form, satz.pfad());
            // Nicht nur uebersetzt, sondern auch angewandt: Ein Muster kann uebersetzen
            // und beim ersten Formatieren trotzdem werfen, wenn es ein Feld verlangt, das
            // ein ZonedDateTime nicht hat.
            assertDoesNotThrow(() -> form.format(ZonedDateTime.now()), satz.pfad());
        }
        assertTrue(geprueft > 0, "ohne ein einziges Muster sichert diese Probe nichts zu");
    }

    @Test
    @DisplayName("9 - alle sechzehn Bereiche stehen im Verzeichnis")
    void alleBereicheStehenImVerzeichnis() {
        // MietSatz ist der zwoelfte - und der ZWEITE Bereich desselben Befehls: Er und
        // LadenSatz schreiben beide Pfade unter laden., damit der Betreiber unter dem
        // Befehl sucht, den er tippt, und trotzdem zwei Haende nie dieselbe Datei teilen.
        // Berufsname steht mit Absicht NICHT dabei: Es ist kein Bereich, sondern eine
        // offene Familie - es gibt einen Schluessel je Beruf der config.yml, und diese
        // Liste verlangt, dass jeder Satz in der ausgelieferten Vorlage steht.
        Set<String> erwartet = new LinkedHashSet<>(List.of("BundSatz", "MarkSatz",
                "ParzellenSatz", "AnfragenSatz", "SpielzeitSatz", "ReiseSatz",
                "WacheSatz", "BerufSatz", "MarktSatz", "BonusSatz", "LadenSatz",
                "MietSatz",
                // Plan 7.4 ist der DREIZEHNTE Bereich - der erste, der nicht zu einem
                // Befehl gehoert: Seine Saetze kommen von einem beschriebenen Schild und
                // von einer Hand an einem fremden Block, nicht von einem getippten Wort.
                "ShopSatz",
                // Das Blockbuch ist der VIERZEHNTE - und der erste, dessen Saetze nicht
                // einem Spieler etwas ueber sein eigenes Tun sagen, sondern einem
                // Moderator etwas ueber das Tun eines anderen. Darum traegt er eigene
                // Zeilensaetze fuer den ABGEWIESENEN Versuch: Der soll sich im ganzen
                // Bild unterscheiden, nicht nur in einem eingesetzten Wort.
                "BlockSatz",
                // Die Freie Truhe ist der FUENFZEHNTE - und der erste, dessen Saetze an
                // einer Kiste haengen, die niemandem gehoert. Er traegt darum zwei Paare
                // mit zwei Blickwinkeln: mit Deckel eine Zahl, ohne Deckel ein eigener
                // Satz, und beim Schliessen eine Zeile fuer das, was mitkommt, und eine
                // fuer das, was dableibt.
                "TruheSatz", "FreistadtSatz"));
        Set<String> gefuehrt = new LinkedHashSet<>();
        Sprachschluessel.bereiche().forEach(bereich ->
                gefuehrt.add(bereich.getSimpleName()));
        // assertEquals auf die Menge und nicht contains: Rot auch dann, wenn ein Bereich
        // doppelt oder ein unerwarteter dabei ist.
        assertEquals(erwartet, gefuehrt);
    }

    @Test
    @DisplayName("10 - die Querverweiszeile nennt den echten Pfad der Rangwerbung")
    void rangWerbungVerweistAufDenEchtenPfad() {
        // Die KONSTANTE und nicht ein Text im Kommentar: So wird bei jeder Umbenennung in
        // core diese Probe rot, statt dass die Zeile in der Vorlage stumm falsch wird.
        assertTrue(Sprachprobe.vorlagentext().contains(Wirtschaftswerte.PFAD_RANG_WERBUNG),
                Sprachliste.VORLAGE + " muss auf " + Wirtschaftswerte.PFAD_RANG_WERBUNG
                        + " verweisen - sonst greppt ein Betreiber dort und findet nichts");
    }

    @Test
    @DisplayName("10b - die eigene Datei wird LEER ausgeliefert")
    void dieEigeneDateiIstLeerAusgeliefert() {
        assertNotNull(Sprachliste.class.getResourceAsStream("/" + Sprachliste.DATEI),
                Sprachliste.DATEI + " liegt nicht in der Jar");
        Node wurzel = new Yaml().compose(new StringReader(Sprachprobe.eigenertext()));
        // Die tragende Voraussetzung des ganzen Geruests: Stuende ein Satz darin, friert
        // saveResource(DATEI, false) ihn auf jedem frischen Server fuer immer ein, und der
        // gemessene Befund "16 von 42 Schluesseln fehlen und wirken trotzdem" kehrt
        // wieder. Gemessen, dass es pruefbar ist: compose auf eine Datei mit nur
        // Kommentaren gibt null.
        assertTrue(wurzel == null || (wurzel instanceof MappingNode leer
                        && leer.getValue().isEmpty()),
                Sprachliste.DATEI + " muss leer ausgeliefert werden, gefunden: " + wurzel);
    }

    @Test
    @DisplayName("10c - das Escape-Beispiel des Kopfes haelt, was es verspricht")
    void escapeBeispielDesKopfesTraegt() {
        String quelle = hinter("BEISPIEL ESCAPE: ");
        String versprochen = hinter("BEISPIEL LIEST: ");
        // Durch den ECHTEN Weg: erst YAML, dann die Tagwache, dann MiniMessage. Damit
        // faengt diese Probe beides - einen einzelnen Backslash (dann wirft YAML, und die
        // ausgelieferte Datei wuerde ueberhaupt nicht laden) und eine Kopfzeile, die etwas
        // anderes verspricht als die Zeile darunter haelt.
        Object gelesen = assertDoesNotThrow(() -> new Yaml().load(quelle),
                "der Kopf zeigt eine YAML-Zeile, die gar nicht laedt: " + quelle);
        String wert = String.valueOf(((Map<?, ?>) gelesen).get("zeile"));
        assertNull(Sprachliste.tagklage("kopf", 19, wert),
                "das Beispiel des Kopfes muss die Tagwache bestehen");
        assertEquals(versprochen, PlainTextComponentSerializer.plainText()
                .serialize(Sprache.MINIMESSAGE.deserialize(wert)));
        assertFalse(versprochen.isBlank(), "das Versprechen des Kopfes fehlt");
    }

    @Test
    @DisplayName("10d - das Umbruch-Beispiel des Kopfes haelt, was es verspricht")
    void umbruchBeispielDesKopfesTraegt() {
        String quelle = hinter("BEISPIEL UMBRUCH: ");
        String versprochen = hinter("BEISPIEL ZWEI ZEILEN: ");
        // Dieselbe Form wie 10c, und derselbe Grund: Der Kopf nennt die Umbruchmarke als
        // Werkzeug des Betreibers, ohne sie zu zeigen. Steht dort die Schreibweise der
        // spitzen Klammer - zwei Backslashes -, liest der Spieler die Marke woertlich im
        // Chat, und keine andere Probe faellt darueber. Die eine Backslashregel gilt
        // eben NICHT fuer beide Faelle: YAML kennt den Umbruch und kennt die Klammer nicht.
        Object gelesen = assertDoesNotThrow(() -> new Yaml().load(quelle),
                "der Kopf zeigt eine YAML-Zeile, die gar nicht laedt: " + quelle);
        String wert = String.valueOf(((Map<?, ?>) gelesen).get("zeile"));
        assertNull(Sprachliste.tagklage("kopf", 0, wert),
                "das Umbruch-Beispiel des Kopfes muss die Tagwache bestehen");

        Satz satz = Sprachprobe.Probesatz.von("kopf.umbruch", Satz.Art.SATZ);
        Sprache sprache = new Sprache(new Sprachstand(
                Sprachprobe.buch(List.of(satz), "kopf.umbruch", wert)));
        var zeilen = sprache.bau(satz, 1L, Einsatz.keine());

        List<String> erwartet = List.of(versprochen.split(" [|] ", -1));
        assertTrue(erwartet.size() > 1,
                "das Versprechen des Kopfes nennt nur eine Zeile: " + versprochen);
        assertEquals(erwartet, zeilen.stream().map(Sprachprobe::nurText).toList());
        // Und die Zusage, um die es wirklich geht: JEDE Zeile behaelt die Farbe des
        // Satzes. Vor dem Umbruch steht sie im Tag, hinter ihm nirgends - wer am Wortlaut
        // teilte statt am Baum, schickte die zweite Zeile farblos los.
        zeilen.forEach(zeile -> assertTrue(Sprachprobe.bild(zeile).startsWith("&7"),
                "die Farbe muss ueber den Umbruch hinweg gelten: "
                        + Sprachprobe.bild(zeile)));
    }

    @Test
    @DisplayName("10e - das Geldwort steht nur noch dort, wo kein Spieler es liest")
    void hartesGeldwortNurNochBeimBetreiber() {
        // DIE NACHFOLGERIN DER ZAEHLUNG AUF Betraege.mark(. Jene Hilfsklasse ist
        // mit Bauschritt 8 geloescht, und eine Probe, die einen Namen sucht, den
        // es nicht mehr gibt, bewacht nur noch sich selbst: Ihre Zahl kann gar
        // nicht mehr steigen. Gesucht wird darum das WORT in einem
        // Zeichenkettenliteral - so faengt sie auch einen neu erfundenen
        // Rueckfall: ein + " Mark", eine Klasse Geld, ein String.format.
        //
        // Dieselbe Bauart wie 10f, und aus demselben Grund (Javadoc dort): Die
        // GESTALT der Stelle ueberlebt eine Umbenennung, der Name nicht.
        List<String> gefunden = new ArrayList<>();
        for (Path datei : Quellen.alle()) {
            var suche = GELDWORT.matcher(Quellen.text(datei));
            while (suche.find()) {
                // Der getroffene TEXT und nicht eine Zahl je Datei: Eine Zahl
                // laesst sich hochdrehen, und dann traegt die Tafel die Erlaubnis
                // fuer einen Satz, den niemand gelesen hat. So kostet jede
                // Anpassung das Einfuegen des Satzes selbst - im Diff sichtbar.
                gefunden.add(datei.getFileName() + " -> " + suche.group());
            }
        }
        assertEquals(GELDWORT_IM_PROGRAMM, gefunden,
                "Das Wort fuer einen Geldbetrag steht in waehrung.betrag, und ueberall"
                        + " dort, wo ein SPIELER es liest, kommt es von da. Eine Zahl zu"
                        + " hoch heisst, ein Satz sagt sein Geldwort wieder selbst -"
                        + " dann aendert der Betreiber die Datei, und im Spiel steht"
                        + " beides nebeneinander. Die erlaubten Stellen gehen alle an"
                        + " den Betreiber und in die Konsole.");
        // Und die zweite Richtung, die 10e von Anfang an hielt: Die Marke
        // verschwindet, sobald ihr Grund weg ist - sie kann nicht stehenbleiben.
        assertFalse(Sprachprobe.vorlagentext().contains(WARNUNG),
                "waehrung.betrag wirkt seit Bauschritt 7 ueberall - die Marke " + WARNUNG
                        + " gehoert aus der Vorlage heraus, sonst warnt sie vor nichts.");
    }

    @Test
    @DisplayName("10g - ein Satzschwanz, der auch im Programm steht, sagt das in der"
            + " Vorlage")
    void halbWirkenderTorhinweisWarntInDerVorlage() {
        // Dieselbe Bauart wie 10e und 10f, und sie hat ihre Arbeit getan: Der Hinweis
        // " - oder durch ein Tor am Bundesmarkt." stand als Schwanz in warp.ziele und
        // wortgleich noch in der Absage am Nether- und Endportal, die bis zum zweiten
        // Zug von Schritt 6 im Programm sass. Darum trug warp.ziele die Marke.
        //
        // Die Probe haelt BEIDE Richtungen, wie 10e: Solange der Schwanz im Programm
        // steht, MUSS die Marke in der Vorlage stehen - und sobald er dort verschwindet,
        // MUSS die Marke heraus. GEMESSEN am 05.11.2026: Als der Satz in die Datei zog,
        // wurde diese Probe von sich aus rot und verlangte die Marke heraus, und sie war
        // dabei die EINZIGE rote Probe des Zuges, die das tat. Das ist der Grund, sie
        // auch jetzt stehen zu lassen: Heute haelt sie die andere Richtung, und eine
        // Marke, die ohne zweiten Ort im Programm zurueckkaeme, waere sofort rot.
        //
        // Der Schwanz steht jetzt in ZWEI Blaettern derselben Datei, warp.ziele und
        // wache.portal.kein-ausgang. Dass die beide dasselbe sagen, prueft niemand mehr
        // - und das ist Absicht: Zwei Saetze mit zwei verschiedenen Anfaengen darf der
        // Betreiber auseinanderentwickeln. In beiden Blaettern steht ein Querverweis
        // auf das andere.
        int imProgramm = 0;
        for (Path datei : Quellen.alle()) {
            imProgramm += Quellen.zaehle(Quellen.text(datei), TORHINWEIS);
        }
        boolean gewarnt = Sprachprobe.vorlagentext().contains(WARNUNG_TORHINWEIS);
        if (imProgramm > 0) {
            assertTrue(gewarnt, "der Hinweis auf das Tor steht noch an " + imProgramm
                    + " Stellen hart im Programm. Solange das so ist, MUSS der Kopf von"
                    + " warp.ziele die Marke " + WARNUNG_TORHINWEIS + " tragen - sonst"
                    + " aendert der Betreiber den Schwanz und liest ihn am Portal"
                    + " weiter im Alten.");
        } else {
            assertFalse(gewarnt, "der Hinweis kommt nirgends mehr aus dem Programm -"
                    + " die Marke " + WARNUNG_TORHINWEIS + " gehoert aus der Vorlage"
                    + " heraus, sonst warnt sie vor nichts.");
        }
    }

    /**
     * Jeder {@code eineZeile}-Aufruf des Programms, mit dem Satz, den er holt.
     *
     * <p>Gesucht wird im Quelltext und nicht in einer Liste von Hand: Eine Liste bliebe
     * beim naechsten Sendeort stehen, und genau dieser naechste Sendeort ist der
     * gefaehrliche.
     */
    private record Holstelle(Path datei, int zeile, Satz satz) {
    }

    /**
     * 10h - KEIN Satz mit Umbruchmarke wird als eine einzige Zeile geholt.
     *
     * <p><b>Diese Probe gibt es, weil derselbe Fehler am 11.11.2026 an ZWEI voellig
     * verschiedenen Stellen stand</b> - und beide Male hatte die Bildtafel den Satz
     * zeichengleich festgehalten, mitsamt seiner Umbruchmarke:
     * <ul>
     *   <li>{@code warp.stadt-unbekannt}: Wer sich bei {@code /warp freistadt <name>}
     *       vertippte, bekam eine rote Brigadier-Zeile statt seiner Absage.</li>
     *   <li>{@code shop.absage.schon-ein-shop}: Wer ein zweites Schild an einen Shop
     *       schrieb, las GAR NICHTS, und der Betreiber bekam je Versuch einen
     *       Stapelabzug.</li>
     * </ul>
     *
     * <p><b>Warum keine Tafel das faengt und auch nicht faengen kann:</b> Eine Bildtafel
     * ruft den Satz SELBST ueber {@code sprache.bau} und schreibt das Bild auf - sie
     * sieht nie, was ein ZWEIG mit demselben Satz tut. Eine Zaehltafel zaehlt Aufrufe
     * und kennt den Wortlaut nicht. Erst beides zusammen - der Aufruf AUS DEM QUELLTEXT
     * und der Wortlaut AUS DER DATEI - ergibt die Frage, die hier gestellt wird.
     *
     * <p><b>Gebaut statt nach Backslash-n gesucht:</b> Geprueft wird die Bedingung, an
     * der {@code Sprache.eineZeile} wirklich wirft - {@code bau(...).size() != 1}. Eine
     * Zeichensuche nach der Umbruchmarke waere eine Abschrift dieser Bedingung, und zwei
     * Abschriften laufen auseinander; sie wuerde ausserdem bei einer Fluchtform blind.
     *
     * <p>Die Umkehrung steht ausdruecklich NICHT hier: Ein Satz darf mehrzeilig sein,
     * solange ihn niemand als eine Zeile holt. Ein Verbot mehrzeiliger Saetze naehme dem
     * Betreiber den Aufbau, und der gehoert ihm (siehe Kopf dieser Datei).
     */
    @Test
    @DisplayName("10h - kein Satz mit Umbruchmarke wird als EINE Zeile geholt")
    void keinMehrzeiligerSatzWirdAlsEineZeileGeholt() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        List<String> kaputt = new ArrayList<>();
        for (Holstelle stelle : holstellen()) {
            int zeilen = sprache.bau(stelle.satz(), 1L, Einsatz.keine()).size();
            if (zeilen != 1) {
                kaputt.add(stelle.datei().getFileName() + ":" + stelle.zeile() + " holt "
                        + stelle.satz().pfad() + " als EINE Zeile, und der Satz hat "
                        + zeilen);
            }
        }
        assertEquals(List.of(), kaputt, "Sprache.eineZeile WIRFT, wenn der Satz mehr als"
                + " eine Zeile ergibt - und zwar mitten im Zweig, der ihn sendet. Der"
                + " Spieler liest dann nichts und der Betreiber einen Stapelabzug."
                + " Entweder diese Stelle holt den Satz ueber sprache.bau und sendet"
                + " jede Zeile, oder die Umbruchmarke gehoert aus dem Satz heraus."
                + " Fast immer ist das Erste richtig: Der zweite Teil eines Satzes ist"
                + " meist der AUSWEG, und eine Absage ohne Ausweg liest sich wie eine"
                + " Sackgasse.");
    }

    /**
     * 10i - jede {@code eineZeile}-Stelle gibt ihren Satz im Quelltext preis.
     *
     * <p>Der Nachbar von 10h, und ohne ihn waere jene Probe eine Behauptung: Sie kann
     * nur Stellen pruefen, deren Satz im Quelltext steht ({@code BundSatz.STAND_KOPF}).
     * Wo er aus einer Variablen kommt, sieht sie nichts - und waere trotzdem gruen.
     *
     * <p>Darum werden diese Stellen hier AUFGESCHRIEBEN, mit Datei und Zeile. Kommt eine
     * dazu, wird diese Probe rot, und wer sie anpasst, muss nachsehen, welche Saetze
     * durch jene Variable laufen koennen. Eine blosse Zahl taete das nicht - sie liesse
     * sich hochdrehen, ohne dass jemand den neuen Sendeort gelesen haette.
     */
    @Test
    @DisplayName("10i - die eineZeile-Stellen ohne sichtbaren Satz sind aufgeschrieben")
    void verdeckteHolstellenSindAufgeschrieben() {
        List<String> verdeckt = new ArrayList<>();
        for (Path datei : Quellen.alle()) {
            String text = Quellen.ohneKommentare(Quellen.text(datei));
            int ab = 0;
            while ((ab = text.indexOf(RUF, ab)) >= 0) {
                int anfang = ab + RUF.length();
                String erstes = ersterWert(text, anfang);
                ab = anfang;
                if (SATZ_IM_TEXT.matcher(erstes).find()) {
                    continue;
                }
                verdeckt.add(datei.getFileName() + " -> "
                        + erstes.replaceAll("\\s+", " ").strip());
            }
        }
        assertEquals(VERDECKTE_HOLSTELLEN, verdeckt, "An diesen Stellen steht der Satz"
                + " nicht im Quelltext, also kann Probe 10h ihn nicht bauen. Kommt eine"
                + " Zeile dazu, sieh nach, welche Saetze durch diese Variable laufen -"
                + " traegt einer davon eine Umbruchmarke, wirft der Zweig im Betrieb.");
    }

    /**
     * Die Stellen, an denen der Satz erst zur Laufzeit feststeht.
     *
     * <p><b>Jede einzeln nachgesehen am 11.11.2026, und alle fuenf sind tragbar - aber
     * aus zwei ganz verschiedenen Gruenden:</b>
     *
     * <ul>
     *   <li>{@code Bonustext -> satz} waehlt zwischen
     *       {@code tagesbonus.gezahlt.bestmarke} und {@code .serie}. Beide sind
     *       einzeilig, und beide sind {@link Satz.Art#SATZ} - hier schuetzt NICHTS
     *       ausser dem Nachsehen. Das ist die eine Stelle, die bei einer Aenderung
     *       wirklich geprueft werden muss.</li>
     *   <li>Die vier anderen holen ausschliesslich {@link Satz.Art#HANDBALKEN}:
     *       {@code Shoptext -> satz} waehlt zwischen {@code shop.schutz.fremdes-schild}
     *       und {@code .fremde-kiste}, {@code Shoptext -> Sprachschluessel.shopHandel}
     *       waehlt aus der Familie {@code shop.handel.*}, und die zwei Stellen in
     *       {@code Sprache} sind {@code sendeHandbalken} und die Deklaration von
     *       {@code eineZeile} selbst. Fuer den Handbalken klagt
     *       {@code Sprachliste.maengelAmWert} schon beim LADEN ueber jede Umbruchmarke -
     *       der Satz kaeme also gar nicht erst durch. Der Riegel liegt dort frueher und
     *       enger als hier.</li>
     * </ul>
     *
     * <p>Aufgeschrieben wird trotzdem jede, und zwar als TEXT: Eine Zahl liesse sich
     * hochdrehen, ohne dass jemand den neuen Sendeort gelesen haette - und genau das ist
     * bei dieser Fehlerklasse schon einmal passiert.
     */
    private static final List<String> VERDECKTE_HOLSTELLEN = List.of(
            "Bonustext.java -> satz",
            "Shoptext.java -> satz",
            "Shoptext.java -> Sprachschluessel.shopHandel(ausgang.bescheid(),"
                    + " ausgang.art(), mitGebuehr)",
            "Sprache.java -> schluessel",
            "Sprache.java -> Satz schluessel");

    private static final String RUF = "eineZeile(";

    /** {@code BundSatz.STAND_KOPF} - ein Satz, den der Quelltext selbst nennt. */
    private static final Pattern SATZ_IM_TEXT =
            Pattern.compile("([A-Z][A-Za-z]*Satz)\\.([A-Z][A-Z0-9_]*)");

    /** Alle Stellen, deren Satz im Quelltext steht - die Grundlage von 10h. */
    private static List<Holstelle> holstellen() {
        Map<String, Satz> nachName = new HashMap<>();
        for (Satz satz : Sprachschluessel.alle()) {
            if (satz instanceof Enum<?> wert) {
                nachName.put(wert.getDeclaringClass().getSimpleName() + "."
                        + wert.name(), satz);
            }
        }
        List<Holstelle> gefunden = new ArrayList<>();
        for (Path datei : Quellen.alle()) {
            String text = Quellen.ohneKommentare(Quellen.text(datei));
            int ab = 0;
            while ((ab = text.indexOf(RUF, ab)) >= 0) {
                int anfang = ab + RUF.length();
                Matcher suche = SATZ_IM_TEXT.matcher(ersterWert(text, anfang));
                // Jeder Satz im ersten Wert, nicht nur der erste: Ein Fragezeichen
                // waehlt zwischen zweien, und BEIDE werden als eine Zeile geholt.
                while (suche.find()) {
                    Satz satz = nachName.get(suche.group());
                    if (satz != null) {
                        gefunden.add(new Holstelle(datei,
                                (int) text.chars().limit(ab).filter(z -> z == 10)
                                        .count() + 1, satz));
                    }
                }
                ab = anfang;
            }
        }
        return gefunden;
    }

    /**
     * Der erste Wert eines Aufrufs - bis zum Komma auf Klammertiefe null.
     *
     * <p>Gezaehlt und nicht bis zum ersten Komma gelesen: {@code eineZeile(satzVon(a, b),
     * werte)} haette sonst beim Komma INNERHALB der Klammer aufgehoert.
     */
    private static String ersterWert(String text, int anfang) {
        int tiefe = 0;
        for (int stelle = anfang; stelle < text.length(); stelle++) {
            char zeichen = text.charAt(stelle);
            if (zeichen == '(') {
                tiefe++;
            } else if (zeichen == ')') {
                if (tiefe == 0) {
                    return text.substring(anfang, stelle);
                }
                tiefe--;
            } else if (zeichen == ',' && tiefe == 0) {
                return text.substring(anfang, stelle);
            }
        }
        return text.substring(anfang);
    }

    @Test
    @DisplayName("10f - eine Reiseaufschrift aus dem Programm sagt das in der Vorlage")
    void halbWirkendeReiseaufschriftWarntInDerVorlage() {
        // Dieselbe Bauart wie 10e, fuer den zweiten halb wirkenden Satz des Projekts.
        // Sechs Saetze unter reise setzen {ziel} ein, und EINE von fuenf Fuellungen ist
        // selbst noch SPIELERTEXT aus dem Programm: "Parzelle 3/-4" bei
        // /freimarken parzelle. "deinen letzten Ort" bei /zurueck ist im zweiten Zug
        // von Schritt 5 umgezogen und steht jetzt unter zurueck.letzter-ort - der Kopf
        // der Vorlage sagt an derselben Sache darum "EINE dieser fuenf Fuellungen".
        // Der Betreiber kann die Saetze umstellen und faerben, diese eine Aufschrift
        // aber nicht - und eine halb wirkende Einstellung ist schlimmer als keine.
        //
        // Gesucht wird nicht nach den WORTEN, sondern nach einem Zeichenkettenliteral im
        // Aufruf von reise.fuehre: So haelt die Probe auch dann, wenn ein kuenftiger
        // Aufrufer eine dritte Aufschrift erfindet - und sie wird von sich aus rot,
        // sobald die letzte umgezogen ist und die Warnung vor nichts mehr warnt.
        int ausDemProgramm = 0;
        for (Path datei : Quellen.alle()) {
            var treffer = AUFSCHRIFT_IM_PROGRAMM.matcher(Quellen.text(datei));
            while (treffer.find()) {
                ausDemProgramm++;
            }
        }
        boolean gewarnt = Sprachprobe.vorlagentext().contains(WARNUNG_REISEZIEL);
        if (ausDemProgramm > 0) {
            assertTrue(gewarnt, "An " + ausDemProgramm + " Stellen geht eine Aufschrift"
                    + " als Zeichenkette aus dem Programm in eine Reise. Solange das so"
                    + " ist, MUSS der Kopf von reise die Marke \"" + WARNUNG_REISEZIEL
                    + "\" tragen - sonst verspricht die Datei eine Wirkung, die sie nicht"
                    + " hat.");
        } else {
            assertFalse(gewarnt, "keine Aufschrift mehr im Programm - die Warnung \""
                    + WARNUNG_REISEZIEL + "\" gehoert aus der Vorlage heraus, sonst warnt"
                    + " sie vor nichts.");
        }
    }

    private static final String WARNUNG = "HALB IN KRAFT:";

    /**
     * Wo das Geldwort im Programm stehen DARF - in Quellreihenfolge, gemessen.
     *
     * <p>Fuenf Stellen in zwei Dateien, und an keine kommt ein SPIELER:
     * {@code FreimarkenPlugin} nennt beim Start das Startguthaben und warnt vor
     * zu billig verkaufter Flaeche - zwei Zeilen an {@code getLogger()}, also nur
     * in die Konsole. {@code Wirtschaftsliste} haengt die Einheit an drei KLAGEN
     * ueber unbrauchbare Zahlen, und die gehen ueber {@code lehneAb} in den Chat
     * eines Betreibers mit {@code freimarken.verwalten} - nicht in den eines Spielers,
     * aber eben doch in einen Chat. Der Kopf von {@code waehrung} in der Vorlage
     * sagt dem Betreiber darum ausdruecklich, dass DIESE Zeile sein Wort nicht
     * uebernimmt.
     *
     * <p><b>Die Grenze dieser Probe:</b> {@link Quellen#alle} listet FLACH nur
     * {@code paper/src/main/java/de/freimarken/paper}. Ein Geldwort in {@code core}
     * oder {@code daten} saehe sie nicht; heute steht dort keines.
     *
     * <p><b>Diese Tafel ist noetig, weil die anderen Tore hier nichts sehen.</b>
     * Ein Betrag, der als fertiger Text in einen Satz eingesetzt wird, nennt keine
     * Adventure-Klasse und liest keinen Satz: {@code GrenzeTest.FARBE} und
     * {@code MINIMESSAGE_LESEN} lassen ihn durch. Ein {@code Einsatz.von(}
     * {@code "betrag", zahl + " Mark")} in irgendeiner Befehlsdatei waere ohne diese
     * Tafel unsichtbar.
     */
    private static final List<String> GELDWORT_IM_PROGRAMM = List.of(
            // Die Einheit in der Klage ueber eine unbrauchbare Wechselgebuehr. Sie geht
            // an den Betreiber und in die Konsole, nie an einen Spieler - wie die drei
            // Stellen in Wirtschaftsliste, deren Helfer sie benutzt.
            "Berufsliste.java -> \"Mark\"",
            // Teil H: Der Leser des Tagesbonus und seine Klagen gehen an den
            // BETREIBER, nicht an einen Spieler. Was ein Spieler liest, kommt
            // aus Bonustext und damit aus waehrung.betrag.
            "Bonusliste.java -> \"Mark je Serientag\"",
            "Bonusliste.java -> \" Mark\"",
            "Bonusliste.java -> \" Mark\"",
            "Bonusliste.java -> \" Mark\"",
            "Bonusliste.java -> \"Mark\"",
            "Bonusliste.java -> \" Bonus eine Nachricht ueber 0 Mark schickt. \"",
            "Bonusliste.java -> \"Mark\"",
            "FreimarkenPlugin.java -> \" Minuten als Mark auf vielen"
                    + " Konten. Lieber kein Parzellensystem\"",
            "FreimarkenPlugin.java -> \" Mark - friert bei der"
                    + " Eroeffnung eines Kontos ein.\"",
            // Die Startzeile, die den Tagesdeckel nennt - sie geht in die Konsole.
            "FreimarkenPlugin.java -> \" Mark je Spieler. Gebucht wird gesammelt,"
                    + " nicht je Block.\"",
            // Die Klage ueber einen verlorenen Verdienst: Serverprotokoll, nie ein
            // Spieler.
            // Die Startzeile zum Bundesmarkt - Konsole. Sie ist am 10.11.2026 zweimal
            // kuerzer geworden: Erst verlor sie "Verkauft wird nur in der
            // Ankunftswelt" (die Weltpruefung fiel), dann die Zahl der Materialien im
            // Sortiment (die Tafel fiel).
            "FreimarkenPlugin.java -> \" Mark je Spieler und Tag.\"",
            // Die Einheit des Marktlesers - sie geht an den Betreiber, der seine
            // config.yml liest, und nie an einen Spieler. Die zwei Erklaerzeilen ueber
            // PFENNIGE sind am 10.11.2026 mit dem Sortiment gegangen.
            "Freistadtliste.java -> \"Mark einmalig\"",
            "Freistadtliste.java -> \" Mark je Gruendung, \"",
            "Marktliste.java -> \"Mark\"",
            // Teil B: Zwei Protokollzeilen des Mietbuchs - die Kaution, die nach dem
            // Loeschen des Vertrags nicht zurueckgezahlt werden konnte, und eine
            // gescheiterte Erstattung. Beide sind SEVERE und von Hand zu heilen; sie
            // liest nur der Betreiber.
            "Mietbuch.java -> \" Mark fuer \"",
            "Mietbuch.java -> \" Mark an \"",
            // Der Leser der Ladenmiete: die Einheiten seiner Klagen und die
            // Rechenprobe, die beim Start und bei jedem Neuladen im Protokoll steht.
            // Alles an den BETREIBER, der seine config.yml liest. Was ein SPIELER
            // liest, kommt aus Miettext und damit aus waehrung.betrag - dass von dort
            // keine Zeile in dieser Tafel steht, ist die Zusage dieses Teils.
            // Teil C: fuenf Protokollzeilen des taeglichen Laufs. Jede geht an den
            // BETREIBER: eine erledigte Zahlung mit ihren Ausfaellen, ein Ausfall mit
            // seiner Zahl, die Kaution beim Ablauf, die verfallene Kaution bei der
            // Raeumung und die SEVERE-Zeile, wenn die Rueckzahlung scheitert. Was der
            // MIETER liest, kommt aus Miettext und damit aus waehrung.betrag - dass von
            // dort keine Zeile in dieser Tafel steht, ist die Zusage dieses Teils.
            "Mietlauf.java -> \" Mark bezahlt - die \"",
            "Mietlauf.java -> \" Mark fehlen (\"",
            "Mietlauf.java -> \" Mark zurueck)\"",
            "Mietlauf.java -> \" Mark verfallen)\"",
            "Mietlauf.java -> \" Mark fuer \"",
            "Mietliste.java -> \"Mark einmalig\"",
            "Mietliste.java -> \" Mark je Tag, \"",
            "Mietliste.java -> \" Mark voller Zeitraum). \"",
            "Mietliste.java -> \" Mark - zurueck beim Kuendigen und beim"
                    + " Ablaufen, nicht\"",
            "Mietliste.java -> \" Mark einmalig. \"",
            // Teil C: der Verlust bis zur Raeumung, NEBEN der Kaution. Genau
            // diese Gegenueberstellung ist der Zweck der Zeile - wer "1.500
            // Mark bis zur Raeumung" neben einer Kaution von 500 liest, sieht,
            // dass das Weglaufen billiger ist als das Zahlen.
            "Mietliste.java -> \" Mark Miete auf. \"",
            "Mietliste.java -> \"Mark je Tag\"",
            "Mietliste.java -> \"Mark\"",
            // Teil C, Befund aus dem gelesenen Startlauf: Die Zeile, die eine
            // Kaution unter dem Verlust bis zur Raeumung benennt. Erlaubt ist
            // sie, aber der Betreiber soll die Folge kennen.
            "Mietliste.java -> \" Mark Miete, die bis zur Raeumung\"",
            // Plan 7.4: die Rechenprobe des Handels. EINE Nennung, und sie steht in
            // einer Zeile, die nur der Betreiber liest - "von 1.000 Mark bleiben
            // 1.000" sagt ihm, was 0 Prozent bedeuten; die Zahl 0 allein sagt
            // niemandem etwas. Die Startzeile daneben nennt kein Geld, nur
            // Prozente - darum steht sie hier nicht.
            // SIEBEN Protokollzeilen des Handels, und alle sieben sind ein
            // Stoerfall: ein gescheitertes Abbuchen, ein Erloes, der nicht
            // angekommen ist, eine Gebuehr, die nicht abzubuchen war, und die
            // Erstattung. Jede nennt ihren Betrag, damit ein Betreiber sie von
            // Hand nachbuchen kann - ohne die Zahl ist die Zeile ein Hinweis und
            // keine Anweisung. Ein SPIELER liest keine davon.
            // DREI KLAGEN STANDEN HIER am Mittag des 10.11.2026 - ueber eine unebene
            // Menge und ueber ein Schild, dessen Zahl von der Ankauftafel abwich. Sie
            // sind mit der Tafel gegangen: Der Preis steht auf dem Schild, und eine
            // Abweichung gibt es nicht mehr.
            "Shophandel.java -> \" Mark bei \"",
            "Shophandel.java -> \" Mark NICHT\"",
            "Shophandel.java -> \" Mark liess sich bei \"",
            "Shophandel.java -> \" Mark bekommen hat. Das kann\"",
            "Shophandel.java -> \" Mark fehlt im Buch.\"",
            "Shophandel.java -> \" Mark zurueck.\"",
            "Shophandel.java -> \" Mark an \"",
            "Shopliste.java -> \" Mark Umsatz bleiben\"",
            // Die Startzeile ueber die Staende des Bundes - Konsole, wie die uebrigen
            // Startzeilen.
            "Shopliste.java -> \" Mark je Spieler und Tag\"",
            // Zwei Protokollzeilen: der Wurf eines Programmfehlers und die
            // Verlustzeile, wenn die Serie nach einer Zahlung nicht geschrieben
            // werden konnte. Beide liest nur der Betreiber.
            "Tagesbonus.java -> \" Mark ist keine - wo nichts gezahlt wurde, steht null\"",
            "Tagesbonus.java -> \" Mark, Serientag \"",
            "Verdienstbuch.java -> \" Mark sind verloren. Es wird NICHT wiederholt:"
                    + " Ein zweiter Versuch\"",
            // Die Einheit und die zwei Erklaerzeilen des Lesers - alle drei gehen an den
            // Betreiber, der seine config.yml liest.
            "Verdienstliste.java -> \"Mark\"",
            "Verdienstliste.java -> \" steht in PFENNIGEN, also in Hundertsteln einer"
                    + " Mark: 40 heisst\"",
            "Verdienstliste.java -> \" 0,40 Mark. Ein Punkt trennt in YAML keine"
                    + " Tausender, sondern die\"",
            // Die Zeile beim Abschalten - Konsole.
            "Verdienstwache.java -> \" Mark Verdienst\"",
            "Wirtschaftsliste.java -> \"Mark\"",
            "Wirtschaftsliste.java -> \"Mark je Feld\"",
            "Wirtschaftsliste.java -> \"Mark\"");

    /**
     * Das Geldwort in einem Zeichenkettenliteral - und nur dort.
     *
     * <p>Die Wortgrenzen sind die Sache selbst: Ohne sie traefe die Suche
     * {@code Marktstadt}, {@code Marke} und {@code Markierung}, und die Tafel
     * stuende voller Stellen, die mit Geld nichts zu tun haben. Kommentare liest
     * {@link Quellen#text} ohnehin nicht mit.
     */
    private static final Pattern GELDWORT = Pattern.compile(
            "\"[^\"]*\\bMark\\b[^\"]*\"");

    /**
     * Die Marke fuer die Aufschrift eines Reiseziels.
     *
     * <p>Eine EIGENE Marke und nicht dieselbe wie {@link #WARNUNG}: Es sind zwei
     * unabhaengige Sachverhalte, und mit einer gemeinsamen Marke koennte 10e nicht mehr
     * zusichern, dass sie verschwindet, sobald ihr Grund weg ist. Der Doppelpunkt steht
     * HINTER der Klammer, damit {@code contains(WARNUNG)} diese Marke nicht trifft.
     */
    private static final String WARNUNG_REISEZIEL = "HALB IN KRAFT (Reiseziel):";

    /**
     * Die Marke fuer den Hinweis, der auch im Programm stand.
     *
     * <p>Seit dem zweiten Zug von Schritt 6 steht sie in der Vorlage NICHT mehr, und
     * genau darauf besteht 10g: Der zweite Ort ist jetzt ein Blatt derselben Datei.
     */
    private static final String WARNUNG_TORHINWEIS = "HALB IN KRAFT (Torhinweis):";

    /**
     * Der Satzschwanz, der an zwei Orten wortgleich steht.
     *
     * <p>Mit dem fuehrenden Gedankenstrich und dem Punkt am Ende: So trifft die Suche
     * den Schwanz und nicht jede Erwaehnung des Bundesmarktes.
     *
     * <p>Gemessen und darum aufgeschrieben: Diese Suche sieht den Schwanz nur, wenn er
     * in EINEM Zeichenkettenliteral steht. Ein Satz, der im Quelltext mit {@code +} auf
     * zwei Zeilen verteilt wird und dabei mitten im Schwanz bricht, bleibt fuer 10g
     * unsichtbar - belegt durch einen Eingriff am 05.11.2026, bei dem ein
     * handgeschriebener Rueckfall in {@code Portalwache} nur 31q rot machte und diese
     * Probe gruen liess. Fuer die Richtung, die heute gilt, ist das harmlos; wer den
     * Schwanz je wieder in das Programm holt, verlaesst sich besser nicht darauf.
     */
    private static final String TORHINWEIS = " - oder durch ein Tor am Bundesmarkt.";

    /**
     * Eine Aufschrift, die als Zeichenkette aus dem Programm in eine Reise geht.
     *
     * <p>{@code [^;]} und nicht {@code .}: So kann ein Treffer nicht vom Aufruf in eine
     * ganz andere Anweisung mit einer Zeichenkette laufen.
     */
    private static final Pattern AUFSCHRIFT_IM_PROGRAMM = Pattern.compile(
            "reise\\s*[.]\\s*fuehre\\s*\\([^;]*\"");

    private static String hinter(String marke) {
        for (String zeile : Sprachprobe.vorlagentext().lines().toList()) {
            int stelle = zeile.indexOf(marke);
            if (stelle >= 0) {
                return zeile.substring(stelle + marke.length());
            }
        }
        throw new AssertionError("Der Kopf von " + Sprachliste.VORLAGE + " nennt \""
                + marke + "\" nicht - ohne das Beispiel ist die Escape-Regel eine"
                + " Behauptung ohne Probe.");
    }
}
