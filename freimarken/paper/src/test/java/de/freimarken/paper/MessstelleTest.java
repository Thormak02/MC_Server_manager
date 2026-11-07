package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Lebenszeichen;
import de.freimarken.kern.Messwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Messstelle OHNE Server - moeglich, weil sie kein Bukkit kennt.
 *
 * <p>Derselbe Schnitt wie bei {@link SpielzeitbuchTest}: Das Spiel liefert genau eine Zahl
 * herein, naemlich den Ort eines Spielers, und das besorgt {@link Messwache}. Damit ist
 * hier pruefbar, was am Server nicht zu pruefen ist, ohne eine Taktlaenge zu warten - der
 * Abstand zweier Orte, der Weltwechsel, das Leeren beim Abrechnen.
 */
@DisplayName("Die Messstelle")
class MessstelleTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private static final UUID BERT = UUID.fromString("0000be27-0000-0000-0000-000000000002");

    private static final String ALTFURT = "freimarken:altfurt";

    private static final String NETHER = "freimarken:nether";

    /** Wo jeder Spieler gerade steht - von Hand gestellt, wie die Pruefuhr. */
    private final Map<UUID, Messstelle.Ort> orte = new HashMap<>();

    private Wirtschaftsstand wirtschaft;
    private Messstelle messstelle;

    @BeforeEach
    void vorher() {
        orte.clear();
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        messstelle = new Messstelle(wirtschaft, orte::get);
    }

    /** Stellt einen Spieler an eine Stelle in Altfurt. */
    private void stelle(UUID wer, double x, double z) {
        orte.put(wer, new Messstelle.Ort(ALTFURT, x, 71.0, z));
    }

    @Test
    @DisplayName("Ohne Lebenszeichen ist ein Schlag nicht taetig")
    void ohneZeichen() {
        stelle(ANNA, 0, 0);

        assertFalse(messstelle.nimmUndLeere(ANNA));
    }

    @Test
    @DisplayName("Ein Zeichen allein genuegt der Vorgabe nicht - zwei schon")
    void einUndZweiZeichen() {
        stelle(ANNA, 0, 0);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);

        assertFalse(messstelle.nimmUndLeere(ANNA));

        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        messstelle.melde(ANNA, Lebenszeichen.OEFFNEN);

        assertTrue(messstelle.nimmUndLeere(ANNA));
    }

    @Test
    @DisplayName("Das Abrechnen LEERT - ein Zeichen faerbt nicht den ganzen Abend")
    void abrechnenLeert() {
        stelle(ANNA, 0, 0);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        messstelle.melde(ANNA, Lebenszeichen.OEFFNEN);

        assertTrue(messstelle.nimmUndLeere(ANNA));
        // Der zweite Schlag faengt bei null an. Ohne das Leeren waere ein Spieler, der
        // einmal zwei Zeichen gegeben hat, bis zum Abmelden taetig - und das sieht wie
        // ein gut gespielter Abend aus, nicht wie ein Fehler.
        assertFalse(messstelle.nimmUndLeere(ANNA),
                "nach dem Abrechnen muss der naechste Schlag bei null anfangen");
    }

    @Test
    @DisplayName("Der ABSTAND gibt das Bewegen - nicht die Zahl der Meldungen")
    void bewegenAusDemAbstand() {
        stelle(ANNA, 0, 0);
        // Der erste Schlag setzt nur den Anfang; es gibt noch keinen Abstand.
        assertFalse(messstelle.nimmUndLeere(ANNA));

        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        stelle(ANNA, 20, 0);

        // Zwanzig Bloecke weiter: abbauen UND bewegen, also taetig.
        assertTrue(messstelle.nimmUndLeere(ANNA));
    }

    @Test
    @DisplayName("Wer an der Wand steht, bewegt sich nicht - auch nach vielen Schlaegen")
    void anDerWandKeinBewegen() {
        // Der Fall, gegen den die Strecke gebaut ist: Eine gewackelte Maus und ein
        // beschwerter Taster erzeugen Bewegungsereignisse im Dutzend und tragen den
        // Spieler null bis zwei Bloecke weit.
        stelle(ANNA, 0, 0);
        messstelle.nimmUndLeere(ANNA);

        for (int schlag = 0; schlag < 10; schlag++) {
            messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
            // Zwei Bloecke hin, zwei zurueck - Rueckstoss am Block.
            stelle(ANNA, schlag % 2 == 0 ? 2 : 0, 0);

            assertFalse(messstelle.nimmUndLeere(ANNA),
                    "zwei Bloecke sind keine Strecke, Schlag " + schlag);
        }
    }

    @Test
    @DisplayName("Im Kreis laufen ist keine Strecke - gemessen wird Anfang zu Ende")
    void imKreisIstKeineStrecke() {
        // Gemessen wird der ABSTAND und nicht der gelaufene Weg. Wer im Kreis laeuft,
        // legt Weg zurueck und kommt nicht weiter - und genau das tut ein Laufmakro.
        stelle(ANNA, 0, 0);
        messstelle.nimmUndLeere(ANNA);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);

        // Mitten im Schlag war er weit weg; am Ende steht er wieder am Anfang.
        stelle(ANNA, 500, 500);
        stelle(ANNA, 0, 0);

        assertFalse(messstelle.nimmUndLeere(ANNA));
    }

    @Test
    @DisplayName("Die HOEHE zaehlt mit - ein Aufzug ist eine Bewegung")
    void hoeheZaehltMit() {
        orte.put(ANNA, new Messstelle.Ort(ALTFURT, 0, 71, 0));
        messstelle.nimmUndLeere(ANNA);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        orte.put(ANNA, new Messstelle.Ort(ALTFURT, 0, 85, 0));

        assertTrue(messstelle.nimmUndLeere(ANNA),
                "vierzehn Bloecke nach oben sind eine Bewegung");
    }

    @Test
    @DisplayName("Ein Weltwechsel zaehlt - auch bei genau gleichen Koordinaten")
    void weltwechselZaehlt() {
        // Gleiche Koordinaten in zwei Welten geben den Abstand 0, und der waere falsch in
        // der unsicheren Richtung.
        orte.put(ANNA, new Messstelle.Ort(ALTFURT, 7, 71, 13));
        messstelle.nimmUndLeere(ANNA);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        orte.put(ANNA, new Messstelle.Ort(NETHER, 7, 71, 13));

        assertTrue(messstelle.nimmUndLeere(ANNA));
    }

    @Test
    @DisplayName("Ein Spieler ohne Ort zaehlt kein Bewegen - die sichere Richtung")
    void ohneOrtKeinBewegen() {
        // Erreichbar: Zwischen dem Takt und der Abrechnung kann ein Spieler die Welt
        // verlassen. Dann gibt die Ortsquelle null, und das ist kein Fehler.
        stelle(ANNA, 0, 0);
        messstelle.nimmUndLeere(ANNA);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        orte.remove(ANNA);

        assertFalse(messstelle.nimmUndLeere(ANNA));
    }

    @Test
    @DisplayName("Jeder Spieler hat seine eigenen Zeichen und seinen eigenen Ort")
    void spielerSindGetrennt() {
        stelle(ANNA, 0, 0);
        stelle(BERT, 0, 0);
        messstelle.nimmUndLeere(ANNA);
        messstelle.nimmUndLeere(BERT);

        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        messstelle.melde(ANNA, Lebenszeichen.OEFFNEN);
        messstelle.melde(BERT, Lebenszeichen.ABBAUEN);
        stelle(BERT, 100, 0);

        // Anna ist taetig, weil sie zwei Zeichen gab; Bert, weil er lief. Verschiedene
        // Wege zum selben Urteil - und keiner davon faerbt den anderen.
        assertTrue(messstelle.nimmUndLeere(ANNA));
        assertTrue(messstelle.nimmUndLeere(BERT));
    }

    @Test
    @DisplayName("Das Bewegen DARF nicht gemeldet werden - es wird gemessen")
    void bewegenNichtMelden() {
        // Ein Aufruf damit ist ein Fehler im Programm und keine Fehleingabe: Wer das
        // Bewegen meldet, hat einen Zuhoerer auf PlayerMoveEvent gebaut, und der zaehlt
        // jede Kopfdrehung als Strecke.
        assertThrows(IllegalArgumentException.class,
                () -> messstelle.melde(ANNA, Lebenszeichen.BEWEGEN));
    }

    @Test
    @DisplayName("Ohne Spieler oder ohne Art geschieht nichts - und es wirft nicht")
    void fehlendeAngabenSchweigen() {
        // Die Wache liest beides aus einem Ereignis; ein Wurf hier waere ein Abbruch
        // mitten in einem Blockereignis.
        messstelle.melde(null, Lebenszeichen.ABBAUEN);
        messstelle.melde(ANNA, null);

        assertEquals(0, messstelle.beobachtete());
    }

    @Test
    @DisplayName("Vergessen raeumt Zeichen UND Ort weg")
    void vergessenRaeumtBeides() {
        stelle(ANNA, 0, 0);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        messstelle.nimmUndLeere(ANNA);

        messstelle.vergiss(ANNA);

        assertEquals(0, messstelle.beobachtete());
        // Und der naechste Beitritt bekommt keinen geschenkten Weg: Haette der alte Ort
        // ueberlebt, waere der Abstand zwischen zwei Sitzungen eine Bewegung.
        stelle(ANNA, 1_000, 1_000);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);

        assertFalse(messstelle.nimmUndLeere(ANNA),
                "ohne Anfang gibt es keinen Abstand - und der alte Anfang ist weg");
    }

    @Test
    @DisplayName("Der Bericht nennt die Zeichen des laufenden Schlages und veraendert nichts")
    void berichtVeraendertNichts() {
        stelle(ANNA, 0, 0);
        messstelle.melde(ANNA, Lebenszeichen.OEFFNEN);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);

        assertEquals(List.of("abbauen", "oeffnen"), namen(messstelle.bericht(ANNA)),
                "in der Reihenfolge der Aufzaehlung und nicht der Meldungen - zwei"
                        + " Spieler nebeneinander sollen vergleichbar sein");
        assertEquals(2, messstelle.zaehlendeBisher(ANNA));
        // Zweimal gefragt: Ein Bericht, der leert, machte das Nachsehen zu einer Tat.
        assertEquals(List.of("abbauen", "oeffnen"), namen(messstelle.bericht(ANNA)));
        assertTrue(messstelle.nimmUndLeere(ANNA),
                "und der Schlag ist danach noch taetig - der Bericht hat nichts genommen");
    }

    @Test
    @DisplayName("Der Bericht zaehlt nur, was ZAEHLT - abgeschaltete Arten stehen nur da")
    void berichtZaehltNurZaehlende() {
        stelle(ANNA, 0, 0);
        messstelle.melde(ANNA, Lebenszeichen.BENUTZEN);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);

        // benutzen steht ab Werk nicht in der Liste. Es wird GENANNT, damit der
        // Betreiber sieht, was er wegnimmt - aber nicht gezaehlt.
        assertEquals(List.of("abbauen", "benutzen"), namen(messstelle.bericht(ANNA)));
        assertEquals(1, messstelle.zaehlendeBisher(ANNA));
    }

    @Test
    @DisplayName("Ein Spieler ohne Zeichen hat einen leeren Bericht und zaehlt 0")
    void leererBericht() {
        assertEquals(List.of(), namen(messstelle.bericht(ANNA)));
        assertEquals(0, messstelle.zaehlendeBisher(ANNA));
    }

    @Test
    @DisplayName("Ein Neuladen erreicht die Messstelle mitten im Schlag")
    void neuladenWirktSofort() {
        // Die Werte werden beim GEBRAUCH gefragt und nicht in ein Feld kopiert - dieselbe
        // Entscheidung wie beim Spielzeitbuch. Ohne das muesste ein Betreiber den Server
        // neu starten, um eine Zahl zu aendern, die beim Start gelesen wurde.
        stelle(ANNA, 0, 0);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);

        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitMessung(
                Messwerte.VORGABE.mitVerlangtenArten(1)));

        assertTrue(messstelle.nimmUndLeere(ANNA),
                "mit verlangte-arten 1 genuegt das eine Zeichen, das schon gesammelt ist");
    }

    @Test
    @DisplayName("Auch die Mindeststrecke erreicht ein Neuladen")
    void neuladenAendertDieStrecke() {
        stelle(ANNA, 0, 0);
        messstelle.nimmUndLeere(ANNA);
        messstelle.melde(ANNA, Lebenszeichen.ABBAUEN);
        stelle(ANNA, 4, 0);

        // Vier Bloecke sind unter der Vorgabe von acht.
        wirtschaft.uebernimm(Wirtschaftswerte.VORGABE.mitMessung(
                Messwerte.VORGABE.mitStrecke(3)));

        assertTrue(messstelle.nimmUndLeere(ANNA),
                "mit mindest-strecke 3 sind vier Bloecke eine Bewegung");
    }

    @Test
    @DisplayName("Ein Ort ohne Welt ist keiner")
    void ortBrauchtEineWelt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Messstelle.Ort(null, 0, 0, 0));
    }

    private static List<String> namen(Set<Lebenszeichen> arten) {
        List<String> namen = new ArrayList<>();
        for (Lebenszeichen art : new LinkedHashSet<>(arten)) {
            namen.add(art.schluessel());
        }
        return namen;
    }
}
