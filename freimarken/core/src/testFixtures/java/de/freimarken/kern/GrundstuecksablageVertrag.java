package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Grundstuecksablage zusagt - geprueft an jeder Umsetzung.
 *
 * <p>Dieselbe Bauart wie {@link BesitzLagerVertrag}, und aus demselben Grund: Eine
 * Zusage, die nur fuer eine von drei Umsetzungen gilt, ist keine. Der Fund vom
 * 11.11.2026 hat das belegt - {@code mitbauerSetzen} verlor im {@code SpeicherLager} den
 * Namen einer Freistadt, und {@code MariaLager} traf es nicht. Eine Probe bei einer
 * Umsetzung haette den Fehler in einer von drei gesucht.
 *
 * <p>Geprueft wird der VERTRAG und nicht die Regeln darueber: Dass ein Grundstueck im
 * Bauland liegen muss und dass nur der Stadtbesitzer zuschneidet, steht im
 * {@link Grundstuecksbuch} und wird dort geprueft. Hier geht es um Schreiben, Lesen,
 * Finden und die Ueberschneidung - also um das, worauf jenes Buch sich verlaesst.
 */
public abstract class GrundstuecksablageVertrag {

    protected static final String WELT = "freimarken:freistaedte";

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    /** Eine leere Ablage der zu pruefenden Art. */
    protected abstract Grundstuecksablage ablage();

    /**
     * Die Nummer einer Freistadt, an der Grundstuecke haengen duerfen.
     *
     * <p><b>Eine Fabrik und keine Konstante</b>, und das ist der Unterschied zwischen
     * den Umsetzungen: In MariaDB zeigt {@code fk_grundstueck_stadt} auf
     * {@code parzellen.id} mit {@code ON DELETE RESTRICT} - eine erfundene Zahl wird
     * dort abgewiesen, und zwar zu Recht. Die Speicherfassung kann das nicht halten (sie
     * kennt die Parzellen nicht) und gibt eine blosse Zahl.
     *
     * <p>Damit prueft dieser Vertrag genau das, was BEIDE zusagen, und laesst jeder ihre
     * eigene Strenge. Ohne die Fabrik waere er auf die schwaechere der beiden
     * festgelegt - und der Fremdschluessel, die staerkste Zusage dieser Tabelle, von
     * keiner Probe beruehrt.
     *
     * @param welt Weltschluessel, in dem die Stadt liegen soll
     * @param feldX Feldnummer - verschieden je Aufruf, sonst kollidiert der eindeutige
     *              Schluessel {@code ort (welt, x, z)}
     */
    protected abstract long stadtAnlegen(String welt, int feldX, int feldZ);

    /** Die Stadt dieser Probe - einmal je Probe angelegt. */
    private long stadt;

    /** Eine zweite Stadt, damit {@code inStadt} wirklich trennt. */
    private long andereStadt;

    /**
     * Vor jeder Probe: raeumen, dann zwei Staedte anlegen.
     *
     * <p><b>Das Raeumen steht HIER und nicht in der Unterklasse</b>, und das ist eine
     * Lehre aus dem ersten Lauf: JUnit ruft {@code @BeforeEach} der OBERKLASSE zuerst.
     * Ein Leeren in der Unterklasse laeuft danach - und loescht die Staedte, die diese
     * Methode gerade angelegt hat. Der Fremdschluessel {@code fk_grundstueck_stadt}
     * wies dann jedes Grundstueck ab, und alle elf Datenproben waren rot.
     */
    @org.junit.jupiter.api.BeforeEach
    void aufbauen() {
        leeren();
        stadt = stadtAnlegen(WELT, 3, -7);
        andereStadt = stadtAnlegen(WELT, 4, -7);
    }

    /**
     * Reste der vorigen Probe wegraeumen.
     *
     * <p>Im Arbeitsspeicher ist nichts zu tun - jede Probe baut dort eine frische
     * Ablage. Eine Datenbank behaelt dagegen, was die vorige Probe geschrieben hat.
     */
    protected void leeren() {
    }

    @Test
    @DisplayName("Angelegt, gefunden - an jeder Zelle der Flaeche")
    void angelegtUndGefunden() {
        Grundstuecksablage ablage = ablage();
        // Ein Rechteck, das NICHT quadratisch ist: Bei gleichen Kanten bliebe ein
        // vertauschtes X/Z unsichtbar.
        Zellquader flaeche = new Zellquader(4, 7, 6, 8);

        Grundstueck neu = ablage.anlegen(WELT, stadt, flaeche, anna, 0L);

        assertTrue(neu.id() > 0L, "die Ablage vergibt eine Nummer");
        assertEquals(stadt, neu.stadtId());
        assertEquals(anna, neu.eigentuemer());
        assertEquals(flaeche, neu.flaeche());
        assertEquals(6, flaeche.zellen(), "3 x 2 Zellen");
        // JEDE der sechs Zellen findet es - nicht nur die Ecke.
        for (int x = 4; x <= 6; x++) {
            for (int z = 7; z <= 8; z++) {
                assertEquals(neu.id(), ablage.anZelle(WELT, x, z).orElseThrow().id(),
                        "Zelle " + x + "/" + z);
            }
        }
    }

    @Test
    @DisplayName("Eine Zelle DANEBEN findet nichts - auf jeder der vier Seiten")
    void danebenIstFrei() {
        Grundstuecksablage ablage = ablage();
        ablage.anlegen(WELT, stadt, new Zellquader(4, 7, 6, 8), anna, 0L);

        // Alle vier Seiten einzeln: Faellt eine Grenze weg, ist das Grundstueck auf
        // genau einer Seite eine Zelle zu gross - und das merkt nur diese Probe.
        assertTrue(ablage.anZelle(WELT, 3, 7).isEmpty(), "links");
        assertTrue(ablage.anZelle(WELT, 7, 7).isEmpty(), "rechts");
        assertTrue(ablage.anZelle(WELT, 4, 6).isEmpty(), "davor");
        assertTrue(ablage.anZelle(WELT, 4, 9).isEmpty(), "dahinter");
    }

    @Test
    @DisplayName("Eine andere WELT findet nichts an derselben Zelle")
    void weltenSindGetrennt() {
        Grundstuecksablage ablage = ablage();
        ablage.anlegen(WELT, stadt, Zellquader.eineZelle(4, 4), anna, 0L);

        assertTrue(ablage.anZelle("freimarken:andere", 4, 4).isEmpty(),
                "dieselbe Zelle in einer anderen Welt ist eine andere Stelle");
    }

    @Test
    @DisplayName("Eine Ueberschneidung wird abgewiesen - auch um eine einzige Zelle")
    void ueberschneidungWirdAbgewiesen() {
        Grundstuecksablage ablage = ablage();
        ablage.anlegen(WELT, stadt, new Zellquader(0, 0, 5, 5), anna, 0L);

        // Der Riegel liegt in der ABLAGE und nicht nur im Buch: Zwischen Pruefen und
        // Schreiben liegt dort eine Luecke, in der ein zweiter Spieler dasselbe tun
        // kann. Nur hier ist beides eine Tat.
        assertThrows(IllegalStateException.class,
                () -> ablage.anlegen(WELT, stadt, new Zellquader(5, 5, 9, 9), bert, 0L),
                "sie teilen genau die Zelle 5/5");
        assertThrows(IllegalStateException.class,
                () -> ablage.anlegen(WELT, stadt, new Zellquader(0, 0, 5, 5), bert, 0L),
                "und ein gleich geschnittenes erst recht");
        // Daneben geht es: Zelle 5 gegen Zelle 6.
        assertTrue(ablage.anlegen(WELT, stadt, new Zellquader(6, 0, 9, 5), bert, 0L)
                .id() > 0L);
    }

    @Test
    @DisplayName("Eine Ueberschneidung in einer ANDEREN Welt ist keine")
    void ueberschneidungNurInDerselbenWelt() {
        Grundstuecksablage ablage = ablage();
        ablage.anlegen(WELT, stadt, new Zellquader(0, 0, 5, 5), anna, 0L);

        // Ohne diese Zusage waere die Stadtfelderwelt die einzige, in der es jemals
        // Grundstuecke geben koennte - und Hafenwerder und Wolkenwerder stehen im Plan.
        assertTrue(ablage.anlegen("freimarken:andere",
                stadtAnlegen("freimarken:andere", 5, -7),
                new Zellquader(0, 0, 5, 5), anna, 0L).id() > 0L);
    }

    @Test
    @DisplayName("inStadt trennt die Staedte, alle trennt die Welten")
    void listenTrennenRichtig() {
        Grundstuecksablage ablage = ablage();
        ablage.anlegen(WELT, stadt, Zellquader.eineZelle(1, 1), anna, 0L);
        ablage.anlegen(WELT, stadt, Zellquader.eineZelle(2, 2), bert, 0L);
        ablage.anlegen(WELT, andereStadt, Zellquader.eineZelle(9, 9), anna, 0L);
        // In einer anderen Welt eine DRITTE Stadtnummer, und das ist keine Willkuer:
        // Eine Stadtnummer ist eine Zeilennummer aus parzellen und damit global
        // eindeutig - dieselbe Stadt kann nicht in zwei Welten liegen. Darum fragt
        // inStadt die Stadt und braucht keine Welt dazu; eine zweite Pruefung auf die
        // Welt waere ein zweiter Ort fuer dieselbe Wahrheit.
        ablage.anlegen("freimarken:andere",
                stadtAnlegen("freimarken:andere", 3, -7),
                Zellquader.eineZelle(1, 1), anna, 0L);

        assertEquals(2, ablage.inStadt(stadt).size(),
                "inStadt fragt die STADT - und die Nummer nennt die Welt schon mit");
        assertEquals(1, ablage.inStadt(andereStadt).size());
        assertEquals(3, ablage.alle(WELT).size(), "alle fragt die WELT");
        assertEquals(0, ablage.inStadt(999L).size(), "eine Stadt ohne Grundstuecke");
    }

    @Test
    @DisplayName("vonSpieler nennt nur, was ihm GEHOERT - nicht, wo er Mitbauer ist")
    void vonSpielerNenntNurEigenes() {
        Grundstuecksablage ablage = ablage();
        Grundstueck annas = ablage.anlegen(WELT, stadt, Zellquader.eineZelle(1, 1),
                anna, 0L);
        ablage.anlegen(WELT, stadt, Zellquader.eineZelle(2, 2), bert, 0L);
        ablage.mitbauerSetzen(annas.id(), Set.of(bert));

        assertEquals(1, ablage.vonSpieler(WELT, anna).size());
        assertEquals(annas.id(), ablage.vonSpieler(WELT, anna).get(0).id());
        assertEquals(1, ablage.vonSpieler(WELT, bert).size(),
                "Bert hat EINES - sein eigenes. Mitbauer zu sein ist kein Besitz,"
                        + " sonst zaehlte er bei jeder Obergrenze doppelt");
    }

    @Test
    @DisplayName("Mitbauer werden gesetzt, ersetzt und geleert - und sonst bleibt alles")
    void mitbauerSetzenUndLeeren() {
        Grundstuecksablage ablage = ablage();
        Grundstueck neu = ablage.anlegen(WELT, stadt, new Zellquader(3, 4, 5, 6),
                anna, 7_000L);
        assertTrue(neu.mitbauer().isEmpty(), "ab Werk niemand");

        ablage.mitbauerSetzen(neu.id(), Set.of(bert));

        Grundstueck danach = ablage.anZelle(WELT, 3, 4).orElseThrow();
        assertEquals(Set.of(bert), danach.mitbauer());
        assertTrue(danach.darfBauen(bert), "und er darf jetzt bauen");
        assertFalse(danach.gehoert(bert), "gehoeren tut es ihm trotzdem nicht");
        // DURCHTRAGEN - die Lehre aus dem Fund vom 11.11.2026: Dort verlor eine Zeile
        // beim Mitbauer-Setzen still ihren Namen. Jedes andere Feld muss stehen.
        assertEquals(anna, danach.eigentuemer());
        assertEquals(stadt, danach.stadtId());
        assertEquals(neu.flaeche(), danach.flaeche());
        assertEquals(7_000L, danach.preis());
        assertEquals(neu.erworbenAm(), danach.erworbenAm());

        // Ersetzend und nicht ergaenzend.
        ablage.mitbauerSetzen(neu.id(), Set.of(anna));
        assertEquals(Set.of(anna), ablage.anZelle(WELT, 3, 4).orElseThrow().mitbauer());
        ablage.mitbauerSetzen(neu.id(), Set.of());
        assertTrue(ablage.anZelle(WELT, 3, 4).orElseThrow().mitbauer().isEmpty());
    }

    @Test
    @DisplayName("Aufgeloest - die Flaeche ist danach wieder frei")
    void aufgeloestUndWiederFrei() {
        Grundstuecksablage ablage = ablage();
        Grundstueck neu = ablage.anlegen(WELT, stadt, new Zellquader(0, 0, 2, 2),
                anna, 0L);

        ablage.entfernen(neu.id());

        assertTrue(ablage.anZelle(WELT, 1, 1).isEmpty());
        assertEquals(0, ablage.inStadt(stadt).size());
        // Und sie laesst sich neu vergeben - sonst waere Aufloesen eine Sperre.
        assertTrue(ablage.anlegen(WELT, stadt, new Zellquader(0, 0, 2, 2), bert, 0L)
                .id() > 0L);
    }

    @Test
    @DisplayName("Eine Nummer, die es nicht gibt, ist ein Fehler - kein stilles Nichts")
    void unbekannteNummerWirft() {
        Grundstuecksablage ablage = ablage();

        // Ein stilles Nichts waere der schlimmere Weg: Ein Befehl, der ein Grundstueck
        // aufloesen will und nichts findet, haette dann Erfolg gemeldet.
        assertThrows(IllegalStateException.class, () -> ablage.entfernen(4711L));
        assertThrows(IllegalStateException.class,
                () -> ablage.mitbauerSetzen(4711L, Set.of(anna)));
    }

    @Test
    @DisplayName("Jedes Grundstueck bekommt eine EIGENE Nummer")
    void nummernSindEindeutig() {
        Grundstuecksablage ablage = ablage();
        List<Long> nummern = new java.util.ArrayList<>();

        for (int zelle = 0; zelle < 5; zelle++) {
            nummern.add(ablage.anlegen(WELT, stadt, Zellquader.eineZelle(zelle, 0),
                    anna, 0L).id());
        }

        assertEquals(5, Set.copyOf(nummern).size(), "fuenf verschiedene Nummern: "
                + nummern);
    }
}
