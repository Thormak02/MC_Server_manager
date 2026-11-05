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
    @DisplayName("9 - alle sieben Bereiche stehen im Verzeichnis")
    void alleBereicheStehenImVerzeichnis() {
        Set<String> erwartet = new LinkedHashSet<>(List.of("BundSatz", "MarkSatz",
                "ParzellenSatz", "AnfragenSatz", "SpielzeitSatz", "ReiseSatz",
                "WacheSatz"));
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
    @DisplayName("10e - ein Wort, das nur halb wirkt, sagt das in der Vorlage")
    void halbWirkendesWortWarntInDerVorlage() {
        // waehrung.betrag ist der erste Schluessel, dessen Wortlaut noch an einer zweiten
        // Stelle steht: Betraege.mark(...) baut "<zahl> Mark" weiter im Programm, und nur
        // die Verlaufszeile fragt heute die Datei. Wer das Wort hier auf "Taler" aendert,
        // liest im Spiel beides nebeneinander - eine halb wirkende Einstellung ist
        // schlimmer als keine. Diese Probe laesst genau zwei Zustaende zu: entweder steht
        // die Warnung im Kopf, oder es gibt keinen zweiten Ort mehr. Sie faellt also auch
        // dann, wenn der letzte Umzug die Warnung STEHEN laesst.
        int zweiterOrt = 0;
        for (Path datei : Quellen.alle()) {
            if (datei.getFileName().toString().equals("Betraege.java")) {
                continue;
            }
            zweiterOrt += Quellen.zaehle(Quellen.text(datei), "Betraege.mark(");
        }
        boolean gewarnt = Sprachprobe.vorlagentext().contains(WARNUNG);
        if (zweiterOrt > 0) {
            assertTrue(gewarnt, "Betraege.mark(...) steht noch an " + zweiterOrt
                    + " Stellen und sagt dort hart \"Mark\". Solange das so ist, MUSS der"
                    + " Kopf von waehrung.betrag die Marke \"" + WARNUNG + "\" tragen -"
                    + " sonst verspricht die Datei eine Wirkung, die sie nicht hat.");
        } else {
            assertFalse(gewarnt, "kein zweiter Ort mehr - die Warnung \"" + WARNUNG
                    + "\" gehoert aus der Vorlage heraus, sonst warnt sie vor nichts.");
        }
    }

    @Test
    @DisplayName("10g - ein Satzschwanz, der auch im Programm steht, sagt das in der"
            + " Vorlage")
    void halbWirkenderTorhinweisWarntInDerVorlage() {
        // Dieselbe Bauart wie 10e und 10f, fuer den dritten halb wirkenden Satz: Der
        // Hinweis " - oder durch ein Tor am Bundesmarkt." steht als Schwanz in
        // warp.ziele - und wortgleich noch in der Absage am Nether- und Endportal,
        // die bis zu ihrem Bauschritt im Programm sitzt. Wer ihn in der Datei
        // aendert, liest ihn am Portal weiter im Alten.
        //
        // Die Probe haelt BEIDE Richtungen, wie 10e: Solange der Schwanz im Programm
        // steht, MUSS die Marke in der Vorlage stehen - und sobald er dort verschwindet,
        // MUSS die Marke heraus. Damit kann der Zwischenstand nicht stumm bleiben und
        // die Warnung nicht ueberleben; Schritt 6 wird von dieser Probe daran
        // erinnert, auch wenn niemand mehr an diesen Kommentar denkt.
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
     * Die Marke fuer die Aufschrift eines Reiseziels.
     *
     * <p>Eine EIGENE Marke und nicht dieselbe wie {@link #WARNUNG}: Es sind zwei
     * unabhaengige Sachverhalte, und mit einer gemeinsamen Marke koennte 10e nicht mehr
     * zusichern, dass sie verschwindet, sobald ihr Grund weg ist. Der Doppelpunkt steht
     * HINTER der Klammer, damit {@code contains(WARNUNG)} diese Marke nicht trifft.
     */
    private static final String WARNUNG_REISEZIEL = "HALB IN KRAFT (Reiseziel):";

    /** Die Marke fuer den Hinweis, der auch noch im Programm steht. */
    private static final String WARNUNG_TORHINWEIS = "HALB IN KRAFT (Torhinweis):";

    /**
     * Der Satzschwanz, der an zwei Orten wortgleich steht.
     *
     * <p>Mit dem fuehrenden Gedankenstrich und dem Punkt am Ende: So trifft die Suche
     * den Schwanz und nicht jede Erwaehnung des Bundesmarktes.
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
