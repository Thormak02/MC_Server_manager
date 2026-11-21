package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Halter - was ein Neuladen tauscht und was es ausdruecklich stehen laesst.
 *
 * <p>Die Zusage, um die es hier geht, ist die haeufigste Falle eines Neuladens: Der Befehl
 * meldet Erfolg, und ein Teil wirkt erst nach dem Neustart. Darum steht der Block "Nur beim
 * Start gelesen" in JEDER Antwort, auch in der, in der eine Klage das Uebernehmen
 * verhindert hat.
 */
class SprachstandTest {

    /** Dasselbe Buch mit genau einem anderen Wortlaut. */
    private static Sprachbuch mitAenderung(Sprachbuch vorbild, String pfad, String neu) {
        Map<String, String> saetze = new LinkedHashMap<>(vorbild.saetze());
        saetze.put(pfad, neu);
        Set<String> ausDerDatei = new LinkedHashSet<>(vorbild.ausDerDatei());
        ausDerDatei.add(pfad);
        return new Sprachbuch(saetze, ausDerDatei, vorbild.muster(), vorbild.farben());
    }

    private static String alsText(List<Component> zeilen) {
        return String.join("\n", zeilen.stream().map(Sprachprobe::nurText).toList());
    }

    @Test
    @DisplayName("22 - uebernimm(null) wirft")
    void uebernimmNullWirft() {
        Sprachstand stand = Sprachprobe.standAusDemJar();
        // Die Sperre, die die Verdrahtung nicht umgehen kann: Sprachliste gibt bei einer
        // Klage buch() == null zurueck, und wer das ungeprueft weiterreicht, bekommt
        // keinen stummen Teiltausch, sondern einen Wurf.
        assertThrows(IllegalArgumentException.class, () -> stand.uebernimm(null));
    }

    @Test
    @DisplayName("23 - NUR_BEIM_START ist GENAU die Liste der Befehlsbeschreibungen")
    void nurBeimStartIstGenauDieseListe() {
        // KEINE ZAHL in der Ueberschrift: Dort stand "diese fuenfzehn", und es waren
        // schon sechzehn - seit /wolke, ohne dass etwas rot wurde. Der Methodenname sagte
        // sogar "DieseZehn". Was die Liste wirklich haelt, ist der Vergleich darunter.
        Set<String> erwartet = new LinkedHashSet<>(List.of(
                "befehl.beschreibung.freimarken", "befehl.beschreibung.parzelle",
                "befehl.beschreibung.mark", "befehl.beschreibung.warp",
                "befehl.beschreibung.zurueck", "befehl.beschreibung.tpa",
                "befehl.beschreibung.tpahere", "befehl.beschreibung.tpaccept",
                "befehl.beschreibung.tpdeny", "befehl.beschreibung.beruf",
                "befehl.beschreibung.markt", "befehl.beschreibung.laden",
                "befehl.beschreibung.freistadt",
                "befehl.beschreibung.shop",
                // Schritt 10: /werder. Auch diese Beschreibung steht seit der Anmeldung
                // im Befehlsbaum - /help zeigt eine geaenderte erst nach einem Neustart.
                "befehl.beschreibung.werder",
                // Schritt 11: /wolke. Derselbe Grund.
                "befehl.beschreibung.wolke",
                // 20.11.2026: /rang. Derselbe Grund - eine geaenderte Beschreibung
                // wirkt erst nach einem Neustart.
                "befehl.beschreibung.rang",
                // 21.11.2026: /menu.
                "befehl.beschreibung.menu",
                // 21.11.2026: /regeln und /hilfe.
                "befehl.beschreibung.regeln",
                "befehl.beschreibung.hilfe"));
        Set<String> gefuehrt = new LinkedHashSet<>();
        Sprachstand.NUR_BEIM_START.forEach(satz -> gefuehrt.add(satz.pfad()));
        // assertEquals auf Groesse UND Inhalt: contains haette auch eine Liste mit einem
        // zehnten Eintrag durchgelassen - und genau dieser zehnte waere ein Satz, den der
        // Betreiber aendert und der stumm nicht wirkt.
        assertEquals(erwartet, gefuehrt);
    }

    @Test
    @DisplayName("24 - eine Befehlsbeschreibung bleibt stehen, alles andere wird getauscht")
    void befehlsbeschreibungenBleibenStehen() {
        Sprachbuch anfang = Sprachprobe.ausDemJar();
        Sprachstand stand = new Sprachstand(anfang);
        Sprachbuch neu = mitAenderung(
                mitAenderung(anfang, BundSatz.BEFEHL_MARK.pfad(), "Dein Konto"),
                MarkSatz.VERLAUF_GRUND_EROEFFNUNG.pfad(), "Anfangsguthaben");

        var uebernahme = stand.uebernimm(neu);

        assertEquals(anfang.wortlaut(BundSatz.BEFEHL_MARK),
                stand.buch().wortlaut(BundSatz.BEFEHL_MARK),
                "die Beschreibung steht seit der Anmeldung im Befehlsbaum - ein Neuladen"
                        + " meldet keinen Befehl neu an");
        assertEquals("Anfangsguthaben",
                stand.buch().wortlaut(MarkSatz.VERLAUF_GRUND_EROEFFNUNG));
        assertEquals(List.of(MarkSatz.VERLAUF_GRUND_EROEFFNUNG.pfad()),
                uebernahme.geaendert(), "gemeldet wird der Schluessel, und nur der, der"
                        + " sich wirklich geaendert hat");
        assertTrue(uebernahme.nichtAngefasst().stream().anyMatch(zeile
                        -> zeile.contains(BundSatz.BEFEHL_MARK.pfad())
                        && zeile.contains("Neustart")),
                "bekommen: " + uebernahme.nichtAngefasst());
    }

    @Test
    @DisplayName("25 - der Block nicht-angefasst steht auch ohne Uebernahme")
    void nichtAngefasstAuchOhneUebernahme() {
        Sprachstand stand = Sprachprobe.standAusDemJar();
        List<String> ohne = stand.nichtAngefasst(null);

        assertFalse(ohne.isEmpty(), "sonst erfaehrt gerade der Betreiber, bei dem etwas"
                + " schiefging, nicht, dass die Beschreibungen ohnehin nicht neuladbar"
                + " sind - und sucht den Fehler an der falschen Zeile");
        assertTrue(ohne.get(0).contains("befehl.beschreibung.*"), ohne.get(0));
        // SIEBZEHN seit /rang: Die Zahl waechst mit jedem Befehl, und sie gehoert in
        // die Zeile - der Betreiber sieht daran, ob seine Aenderung dabei ist. Hier ist
        // die Zahl eine Zusicherung ueber die Zeile und keine Ueberschrift: Sie steht im
        // Text, den er liest.
        assertTrue(ohne.get(0).contains("20 Saetze"), ohne.get(0));
        // BEIDE LESER [21.11.2026]: Seit es /hilfe gibt, liest ein zweiter Befehl
        // dieselben Saetze aus demselben Buch. Nannte die Zeile nur /help, verbesserte
        // der Betreiber eine Beschreibung, las die Einschraenkung - und tippte dann
        // /hilfe, weil das eine gewoehnliche Chatliste ist wie jede andere. Dort stand
        // sein Tippfehler weiter, und er suchte den Fehler an der falschen Zeile: genau
        // das Bild, gegen das diese Zeile antritt.
        assertTrue(ohne.get(0).contains("/help UND /hilfe"), ohne.get(0));
    }

    @Test
    @DisplayName("25b - der Tausch ist EIN Stueck")
    void tauschIstEinStueck() {
        Sprachbuch anfang = Sprachprobe.ausDemJar();
        Sprachstand stand = new Sprachstand(anfang);
        Sprachbuch neu = mitAenderung(
                mitAenderung(anfang, MarkSatz.VERLAUF_GRUND_BESTAND.pfad(), "Altbestand"),
                MarkSatz.VERLAUF_GRUND_ZUSAMMENLEGUNG.pfad(), "Zusammengelegt");

        Sprachbuch vorher = stand.buch();
        // Der Wortlaut als Zeichenkette festgehalten, BEVOR getauscht wird: Faellt die
        // Schutzkopie in Sprachbuch weg und schreibt uebernimm in die alte Abbildung,
        // zeigen beide Buecher danach dasselbe - ein Vergleich Buch gegen Buch waere dann
        // gruen, obwohl genau der Mangel da ist, den diese Probe sucht.
        String vorherWort = vorher.wortlaut(MarkSatz.VERLAUF_GRUND_BESTAND);
        stand.uebernimm(neu);

        // Kein Leser sieht einen Satz aus dem alten neben einem aus dem neuen Buch: Was er
        // in der Hand hat, ist ein unveraenderliches record, und getauscht wird die ganze
        // Zelle.
        assertEquals("Altbestand", stand.buch().wortlaut(MarkSatz.VERLAUF_GRUND_BESTAND));
        assertEquals("Zusammengelegt",
                stand.buch().wortlaut(MarkSatz.VERLAUF_GRUND_ZUSAMMENLEGUNG));
        assertEquals(vorherWort, vorher.wortlaut(MarkSatz.VERLAUF_GRUND_BESTAND),
                "das alte Buch bleibt vollstaendig, wie es war");
        assertEquals(anfang.wortlaut(MarkSatz.VERLAUF_GRUND_BESTAND), vorherWort);
    }

    @Test
    @DisplayName("25c - die Antwort bei einer Klage sagt, dass nichts uebernommen wurde")
    void sprachAntwortKlage() {
        Sprachbuch anfang = Sprachprobe.ausDemJar();
        Sprachstand stand = new Sprachstand(anfang);
        String klage = "sprache.yml, Zeile 31, Spalte 54, mark.verlauf.zeile: <gol> ist"
                + " keine Farbe. Meintest du <gold>?";

        String antwort = alsText(stand.antwort(
                Sprachliste.nichtsGelesen(klage)));

        assertTrue(antwort.contains("NICHT uebernommen"), antwort);
        assertTrue(antwort.contains("Zeile 31"), "die Zeilennummer muss in der Antwort"
                + " stehen, sonst sucht der Betreiber sie in der Datei: " + antwort);
        assertTrue(antwort.contains("Es gelten weiter"), antwort);
        assertEquals(anfang.wortlaut(MarkSatz.VERLAUF_ZEILE),
                stand.buch().wortlaut(MarkSatz.VERLAUF_ZEILE),
                "eine Klage tauscht gar nichts");
    }

    @Test
    @DisplayName("25d - die Antwort nennt die geaenderten SCHLUESSEL, nicht die Wortlaute")
    void sprachAntwortUebernommen() {
        Sprachbuch anfang = Sprachprobe.ausDemJar();
        Sprachstand stand = new Sprachstand(anfang);
        Sprachbuch neu = mitAenderung(anfang, MarkSatz.VERLAUF_GRUND_BESTAND.pfad(),
                "Altbestand bei der Einfuehrung");

        String antwort = alsText(stand.antwort(
                new Sprachliste.Ergebnis(neu, List.of(), List.of())));

        assertTrue(antwort.contains(MarkSatz.VERLAUF_GRUND_BESTAND.pfad()), antwort);
        // Eine Antwort mit 231 Wortlauten liest niemand - und wer einen Satz geaendert
        // hat, weiss, was er getippt hat.
        assertFalse(antwort.contains("Altbestand bei der Einfuehrung"),
                "der Wortlaut gehoert nicht in die Antwort: " + antwort);
        assertEquals("Altbestand bei der Einfuehrung",
                stand.buch().wortlaut(MarkSatz.VERLAUF_GRUND_BESTAND));
    }

    @Test
    @DisplayName("22g - eine geaenderte Palette REIST MIT und steht in der Meldung")
    void geaendertePaletteReistMit() {
        // DER EINZIGE WEG, auf dem eine geaenderte Farbe im laufenden Server ankommt
        // [21.11.2026]. Bis heute reichte keine Probe dem Tausch ein Buch mit einer
        // ANDEREN Palette herein: mitAenderung kopiert vorbild.farben(), also waren alt
        // und neu immer gleich und der Farbzweig von uebernimm lief nie. Schriebe jemand
        // dort alt.farben() statt ausDerDatei.farben(), blieben alle Proben gruen - und
        // im Spiel aenderte der Betreiber farben: warnung, bekaeme "an den Saetzen hat
        // sich nichts geaendert" zu lesen und die neue Farbe erst nach einem Neustart.
        var gelesen = Sprachliste.ausQuelle("farben:\n  warnung: \"#ff0000\"\n",
                Sprachprobe.ausDemJar());
        assertEquals(List.of(), gelesen.klagen(), "die Quelle selbst muss sauber sein");

        Sprachstand stand = Sprachprobe.standAusDemJar();
        int vorher = stand.buch().farben().wert(Farbtafel.WARNUNG);
        String antwort = alsText(stand.antwort(gelesen));

        // (a) DER WERT ist im Buch angekommen.
        assertEquals(0xff0000, stand.buch().farben().wert(Farbtafel.WARNUNG),
                "die neue Farbe steht nicht im Buch - sie reist nicht mit");
        // (b) UND SIE WIRKT: dasselbe geprueft am BILD eines Satzes, den dieselbe
        // Sprache-Instanz danach baut. Ohne diese Haelfte waere auch ein Buch gruen,
        // das die Zahl traegt und seinen Leser nicht erneuert hat.
        assertEquals("&#ff0000Anna ist nicht da.",
                Sprachprobe.bild(new Sprache(stand).bau(AnfragenSatz.FRAGE_NICHT_DA, 1L,
                        Einsatz.von("name", "Anna")).get(0)),
                "der Satz traegt die alte Farbe - der Leser wurde nicht neu gebaut");
        // (c) DIE MELDEZEILE nennt die Rolle und BEIDE Werte in der Richtung alt -> neu.
        // Ein Vertauschen der zwei in Farbtafel.unterschiede waere sonst nicht zu sehen.
        assertTrue(antwort.contains("farben.warnung"),
                "die Meldung nennt die geaenderte Rolle nicht: " + antwort);
        assertTrue(antwort.contains(Farbtafel.wieInDerDatei(vorher)
                        + " -> " + Farbtafel.wieInDerDatei(0xff0000)),
                "die Meldung nennt nicht beide Werte in der Richtung alt -> neu: "
                        + antwort);
    }

    @Test
    @DisplayName("25e - der Block nicht-angefasst steht in ALLEN drei Lagen")
    void sprachAntwortNenntNichtAngefasstImmer() {
        Sprachbuch anfang = Sprachprobe.ausDemJar();
        String marke = "Nur beim Start gelesen";

        String klage = alsText(new Sprachstand(anfang)
                .antwort(Sprachliste.nichtsGelesen("irgendetwas stimmt nicht")));
        String nichts = alsText(new Sprachstand(anfang)
                .antwort(new Sprachliste.Ergebnis(anfang, List.of(), List.of())));
        String getauscht = alsText(new Sprachstand(anfang).antwort(
                new Sprachliste.Ergebnis(mitAenderung(anfang,
                        MarkSatz.VERLAUF_GRUND_BESTAND.pfad(), "Altbestand"),
                        List.of(), List.of())));

        assertTrue(klage.contains(marke), "Lage KLAGE: " + klage);
        assertTrue(nichts.contains(marke), "Lage NICHTS GEAENDERT: " + nichts);
        assertTrue(getauscht.contains(marke), "Lage UEBERNOMMEN: " + getauscht);
        assertTrue(nichts.contains("nichts geaendert"), nichts);
    }
}
