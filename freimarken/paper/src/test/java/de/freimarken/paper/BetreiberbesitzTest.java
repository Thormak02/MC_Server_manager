package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Lagerattrappe;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Besitz von Hand - die Regel, ohne Server.
 *
 * <p>Jeder Ausgang bekommt seine Probe. Zwei davon sind der Grund, aus dem es diese
 * Klasse gibt: Ein unbekannter Name darf NICHT uebertragen (die Parzelle gehoerte danach
 * einer erfundenen Kennung, und kein Befehl holt sie zurueck), und ein einzelnes Feld
 * einer Gruppe darf nicht durchgeschrieben werden.
 *
 * <p><b>Die Probe nennt PARZELLENNUMMERN</b>, nicht Weltkoordinaten - genau wie der
 * Betreiber am Befehl. Die Umrechnung steht in {@link Betreiberbesitz}.
 *
 * <p>Hier stand eine Probe {@code trifftDieMitte}, die zu halten vorgab, dass die
 * Umrechnung {@code mitteX} nimmt und nicht {@code ursprungX}. Sie konnte das nicht
 * [22.11.2026]: Beide Koordinaten liegen in derselben Rasterzelle, und ueber das Ergebnis
 * sind sie grundsaetzlich nicht unterscheidbar - die Probe war gruen mit UND ohne die
 * Zusage. Sie ist entfernt statt umgeschrieben; eine Grenze, die nie getroffen wird, ist
 * keine Grenze.
 */
@DisplayName("Besitz von Hand")
class BetreiberbesitzTest {

    private static final String WELT = "freimarken:marktstadt";
    private static final long REICH = 10_000_000L;
    private static final int VIELE = 99;

    private static final Wirtschaftsstand VORGABE =
            new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();
    private final UUID clara = UUID.randomUUID();

    private SpeicherLager lager;
    private Besitzbuch buch;

    @BeforeEach
    void aufbauen() {
        lager = new SpeicherLager();
        Kasse kasse = new SpeicherKasse(REICH);
        buch = new Besitzbuch(Stadtgrenze.marktstadt(), lager, kasse, VORGABE);
    }

    /** Die Mitte der Parzelle n auf einer Achse - bei Raster 64 + 7 ist das n*71 + 32. */
    private static int mitte(int parzelle) {
        return parzelle * 71 + 32;
    }

    /** Die Namenssuche der Proben: zwei bekannte Namen, alles andere unbekannt. */
    private Betreiberbesitz.Namenssuche namen() {
        return name -> switch (name) {
            case "Anna" -> anna;
            case "Bert" -> bert;
            case "Clara" -> clara;
            default -> null;
        };
    }

    // --- Uebertragen ------------------------------------------------------------

    @Test
    @DisplayName("Uebertragen setzt den neuen Eigentuemer und nennt seine Zahl")
    void uebertragen() {
        lager.anlegen(WELT, 3, 4, anna, 25_000L);

        var ergebnis = Betreiberbesitz.uebertrage(buch, namen(), WELT, 3, 4, "Bert");

        assertEquals(Betreiberbesitz.Ausgang.UEBERTRAGEN, ergebnis.ausgang());
        assertEquals("Bert", ergebnis.spieler());
        assertEquals(3, ergebnis.parzelleX());
        assertEquals(4, ergebnis.parzelleZ());
        assertEquals(1, ergebnis.haeltJetzt(), "die Zahl steht in der Antwort, weil die"
                + " Rangobergrenze hier nicht geprueft wird");
        assertEquals(bert, lager.finde(WELT, 3, 4).orElseThrow().eigentuemer());
        assertEquals(0, lager.anzahlVon(WELT, anna));
    }

    @Test
    @DisplayName("Die Mitbauer des Vorbesitzers fallen weg")
    void mitbauerFallenWeg() {
        var parzelle = lager.anlegen(WELT, 5, 5, anna, 0L);
        lager.mitbauerSetzen(parzelle.id(), Set.of(clara));
        assertTrue(lager.finde(WELT, 5, 5).orElseThrow().darfBauen(clara),
                "die Probe braucht einen eingetragenen Mitbauer");

        Betreiberbesitz.uebertrage(buch, namen(), WELT, 5, 5, "Bert");

        var danach = lager.finde(WELT, 5, 5).orElseThrow();
        // Blieben sie stehen, erbte bert eine Fremde mit Baurecht auf seinem Grund, ohne
        // dass ihm das irgendwo gesagt wird - und anna waere ueber ihre Mitbauerin
        // weiterhin mittelbar beteiligt.
        assertFalse(danach.darfBauen(clara));
        assertFalse(danach.darfBauen(anna));
        assertTrue(danach.mitbauer().isEmpty());
    }

    @Test
    @DisplayName("Einen unbekannten Namen weist es ab, und nichts aendert sich")
    void unbekannterName() {
        lager.anlegen(WELT, 3, 4, anna, 0L);

        var ergebnis = Betreiberbesitz.uebertrage(buch, namen(), WELT, 3, 4, "anna");

        // Gross- und Kleinschreibung zaehlt - genau wie bei /freimarken geld. Der Riegel
        // ist hier aber teurer: getOfflinePlayer erfindet fuer einen unbekannten Namen
        // eine Kennung, und die Parzelle gehoerte danach niemandem, der sich anmelden
        // kann.
        assertEquals(Betreiberbesitz.Ausgang.UNBEKANNTER_SPIELER, ergebnis.ausgang());
        assertEquals("anna", ergebnis.spieler());
        assertEquals(anna, lager.finde(WELT, 3, 4).orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Auf denselben Eigentuemer uebertragen ist kein Erfolg")
    void schonEigentuemer() {
        lager.anlegen(WELT, 3, 4, anna, 0L);

        var ergebnis = Betreiberbesitz.uebertrage(buch, namen(), WELT, 3, 4, "Anna");

        // Ein "Erledigt." liest der Betreiber als Beweis, dass der Name stimmte - und
        // genau den hatte er womoeglich verwechselt.
        assertEquals(Betreiberbesitz.Ausgang.SCHON_EIGENTUEMER, ergebnis.ausgang());
    }

    @Test
    @DisplayName("Freies Bauland ist NOCH_FREI und nicht KEIN_BESITZ")
    void nochFrei() {
        var ergebnis = Betreiberbesitz.uebertrage(buch, namen(), WELT, 3, 4, "Bert");

        // Der Unterschied ist fuer den Betreiber der zwischen "die Stelle gibt es nicht"
        // und "die Stelle gibt es, sie gehoert nur niemandem".
        assertEquals(Betreiberbesitz.Ausgang.NOCH_FREI, ergebnis.ausgang());
        assertTrue(lager.finde(WELT, 3, 4).isEmpty());
    }

    @Test
    @DisplayName("Jenseits der Stadtgrenze ist die Antwort KEIN_BESITZ")
    void jenseitsDerGrenze() {
        int weit = Stadtgrenze.marktstadt().bis() + 1;

        var ergebnis = Betreiberbesitz.uebertrage(buch, namen(), WELT, weit, weit,
                "Bert");

        // GEMESSEN und nicht erwartet [22.11.2026]: Hier stand AUSSERHALB, und die
        // Probe wurde rot. Das Buch unterscheidet an dieser Stelle nur zwischen "auf
        // Bauland innerhalb der Grenze" (NOCH_FREI) und allem anderen; AUSSERHALB
        // entsteht ausschliesslich in nehmen. Ein eigener Ausgang dafuer waere ein
        // Ausgang, den nie etwas erreicht.
        assertEquals(Betreiberbesitz.Ausgang.KEIN_BESITZ, ergebnis.ausgang());
        assertTrue(lager.finde(WELT, weit, weit).isEmpty());
    }

    @Test
    @DisplayName("Uebertragen nimmt das ganze Grundstueck mit - von JEDER Stelle darin")
    void gruppeWechseltGanz() {
        gruppeAusZweiFeldern();

        // Das zweite Feld, NICHT die Ankerzelle - und genau daran haengt der Fund vom
        // 22.11.2026: zustaendigerBesitz gibt bei einer Gruppe immer das Kind zurueck,
        // auch an der Ankerzelle. Mit AufGruppe.VERWEIGERN waere ein zusammengelegtes
        // Grundstueck ueber KEINEN Weg uebertragbar gewesen, und auftrennen kann nur
        // sein Eigentuemer - der womoeglich gar nicht mehr da ist.
        var ergebnis = Betreiberbesitz.uebertrage(buch, namen(), WELT, 1, 0, "Bert");

        assertEquals(Betreiberbesitz.Ausgang.UEBERTRAGEN, ergebnis.ausgang());
        // Durchgeschrieben auf BEIDE Felder: Traegt nur die Gruppenzeile den Neuen,
        // baut der Alte weiter, waehrend /parzelle liste den Neuen zeigt.
        assertEquals(bert, lager.finde(WELT, 0, 0).orElseThrow().eigentuemer());
        assertEquals(bert, lager.finde(WELT, 1, 0).orElseThrow().eigentuemer());
        assertFalse(lager.finde(WELT, 0, 0).orElseThrow().darfBauen(anna));
    }

    @Test
    @DisplayName("Freigeben bleibt auf einem Gruppenfeld verweigert")
    void freigebenAufGruppeVerweigert() {
        gruppeAusZweiFeldern();

        var ergebnis = Betreiberbesitz.gibFrei(buch, namen(), WELT, 1, 0);

        // Der Unterschied zum Uebertragen, und er ist Absicht: Freigeben LOESCHT. Ein
        // einzelnes Feld durchgeschrieben loeschte das ganze Grundstueck - dieselbe
        // Verweigerung, die /parzelle aufgeben ausspricht.
        assertEquals(Betreiberbesitz.Ausgang.TEIL_EINER_GRUPPE, ergebnis.ausgang());
        assertTrue(lager.finde(WELT, 0, 0).isPresent());
        assertTrue(lager.finde(WELT, 1, 0).isPresent());
    }

    /** Zwei Felder von 0/0 nach Osten, zu einem Grundstueck verbunden. */
    private void gruppeAusZweiFeldern() {
        lager.anlegen(WELT, 0, 0, anna, 0L);
        lager.anlegen(WELT, 1, 0, anna, 0L);
        assertEquals(Besitzbuch.Bescheid.ERTEILT,
                buch.zusammenlegen(WELT, anna, mitte(0), 32, Richtung.OST, VIELE)
                        .bescheid(), "die Probe braucht eine Gruppe");
    }

    @Test
    @DisplayName("Der Vorbesitzer steht im Ergebnis - auch beim Uebertragen")
    void vorbesitzerStehtImErgebnis() {
        lager.anlegen(WELT, 2, 2, anna, 0L);

        var ergebnis = Betreiberbesitz.uebertrage(buch, namen(), WELT, 2, 2, "Bert");

        // Ohne ihn merkt ein Betreiber, der sich in der Nummer vertippt hat, am Satz
        // nicht, dass er jemandem etwas weggenommen hat - und niemand kann den Griff
        // zurueckdrehen.
        assertEquals(Betreiberbesitz.Ausgang.UEBERTRAGEN, ergebnis.ausgang());
        assertEquals(anna.toString(), ergebnis.vorher());
        assertEquals("Bert", ergebnis.spieler());
    }

    @Test
    @DisplayName("Auch bei einem unbekannten Namen steht die gemeinte Stelle im Ergebnis")
    void vorbesitzerAuchBeiUnbekanntemNamen() {
        lager.anlegen(WELT, 2, 3, anna, 0L);

        var ergebnis = Betreiberbesitz.uebertrage(buch, namen(), WELT, 2, 3, "anna");

        // Dann steht im Protokoll, WELCHE Stelle gemeint war - sonst ist aus der Zeile
        // nicht zu lesen, wessen Besitz beinahe gewechselt haette.
        assertEquals(Betreiberbesitz.Ausgang.UNBEKANNTER_SPIELER, ergebnis.ausgang());
        assertEquals(anna.toString(), ergebnis.vorher());
    }

    @Test
    @DisplayName("Die Zahl darf den vollzogenen Wechsel nicht umwerfen")
    void zahlUnbekanntIstKeinFehlschlag() {
        // Betreiberbesitz holt die Zahl der Parzellen NACH dem Wechsel. Bricht die
        // Verbindung in diesem Augenblick weg, flog die Ausnahme vorher bis in den
        // catch des Zweigs, und der Betreiber las "es ist NICHTS geaendert" - obwohl
        // die Parzelle dauerhaft gewechselt hatte [Fund 22.11.2026].
        Lagerattrappe attrappe = new Lagerattrappe() {
            @Override
            public java.util.List<de.freimarken.kern.Parzelle> vonSpieler(
                    String welt, UUID spieler) {
                throw new IllegalStateException("Verbindung weg");
            }
        };
        attrappe.anlegen(WELT, 7, 7, anna, 0L);
        Besitzbuch sperrig = new Besitzbuch(Stadtgrenze.marktstadt(), attrappe,
                new SpeicherKasse(REICH), VORGABE);

        var ergebnis = Betreiberbesitz.uebertrage(sperrig, namen(), WELT, 7, 7, "Bert");

        assertEquals(Betreiberbesitz.Ausgang.UEBERTRAGEN, ergebnis.ausgang(),
                "der Wechsel ist geschehen und wird so gemeldet");
        assertEquals(Betreiberbesitz.ZAHL_UNBEKANNT, ergebnis.haeltJetzt(),
                "nur die Zahl fehlt, und das sagt die Antwort");
        assertEquals(bert, attrappe.finde(WELT, 7, 7).orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Ohne Buch oder Namenssuche gibt es keine Uebertragung")
    void ohneWerkzeug() {
        assertThrows(IllegalArgumentException.class,
                () -> Betreiberbesitz.uebertrage(null, namen(), WELT, 1, 1, "Bert"));
        assertThrows(IllegalArgumentException.class,
                () -> Betreiberbesitz.uebertrage(buch, null, WELT, 1, 1, "Bert"));
    }

    // --- Freigeben --------------------------------------------------------------

    @Test
    @DisplayName("Freigeben macht die Stelle wieder zu Bauland und nennt den Vorbesitzer")
    void freigeben() {
        lager.anlegen(WELT, 6, 7, anna, 25_000L);

        var ergebnis = Betreiberbesitz.gibFrei(buch, namen(), WELT, 6, 7);

        assertEquals(Betreiberbesitz.Ausgang.FREIGEGEBEN, ergebnis.ausgang());
        // Der Vorbesitzer wird VOR der Tat gelesen: Danach gibt es ihn in der Zeile
        // nicht mehr, und ohne seine Kennung stuende im Protokoll nur, dass irgendwo
        // etwas freigegeben wurde.
        assertEquals(anna.toString(), ergebnis.spieler());
        assertTrue(lager.finde(WELT, 6, 7).isEmpty());
        assertEquals(0, lager.anzahlVon(WELT, anna));
    }

    @Test
    @DisplayName("Freigeben einer freien Stelle ist NOCH_FREI, nicht FREIGEGEBEN")
    void freigebenVonNichts() {
        var ergebnis = Betreiberbesitz.gibFrei(buch, namen(), WELT, 6, 7);

        assertEquals(Betreiberbesitz.Ausgang.NOCH_FREI, ergebnis.ausgang());
        assertEquals(null, ergebnis.spieler(), "es gab keinen Vorbesitzer");
    }

    @Test
    @DisplayName("Ohne Buch gibt es keine Freigabe")
    void freigebenOhneBuch() {
        assertThrows(IllegalArgumentException.class,
                () -> Betreiberbesitz.gibFrei(null, namen(), WELT, 1, 1));
    }
}
