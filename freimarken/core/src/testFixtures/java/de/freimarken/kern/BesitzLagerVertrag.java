package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link BesitzLager} leisten muss.
 *
 * <p>Absichtlich als Vertrag und nicht als Test einer Klasse: Wenn die Datenbankfassung
 * dazukommt, erbt sie genau diese Pruefungen. Damit ist der Umstieg kein Vertrauensakt,
 * sondern ein gruener Testlauf - und ein Unterschied im Verhalten faellt sofort auf,
 * statt im Betrieb.
 */
public abstract class BesitzLagerVertrag {

    protected static final String WELT = "freimarken:marktstadt";

    /**
     * Die Welt der Freistaedte - eine ANDERE als {@link #WELT}, und das ist tragend.
     *
     * <p>In MariaLager zaehlt {@code anzahlVon} alles, was auf dem Raster liegt, ohne
     * nach der Art zu fragen. Staenden Staedte und Parzellen in derselben Welt, zaehlte
     * eine Freistadt als Parzelle mit - die naechste Parzelle koestete das Doppelte und
     * die Rangobergrenze waere frueher erreicht. Der Riegel ist der WELTSCHLUESSEL, nicht
     * die Art, und diese Konstante ist die Stelle, an der das sichtbar bleibt.
     */
    protected static final String STADTWELT = "freimarken:freistaedte";

    /**
     * Die Welt der Meerinseln - eine DRITTE, aus genau dem Grund von {@link #STADTWELT}.
     *
     * <p>{@code anzahlVon} zaehlt alles, was auf dem Raster liegt, ohne nach der Art zu
     * fragen. Laegen Inseln und Parzellen in derselben Welt, zaehlte eine Insel als
     * Parzelle mit - die naechste Parzelle koestete das Doppelte und die Rangobergrenze
     * waere frueher erreicht. Der Riegel ist der WELTSCHLUESSEL, nicht die Art.
     */
    protected static final String INSELWELT = "freimarken:hafenwerder";

    /**
     * Die Welt der Wolkeninseln - eine VIERTE, aus genau dem Grund von {@link #STADTWELT}.
     *
     * <p>{@code anzahlVon} zaehlt alles, was auf dem Raster liegt, ohne nach der Art zu
     * fragen. Der Riegel ist der WELTSCHLUESSEL, nicht die Art - und hier traegt er
     * noch etwas: Wer eine Meerinsel haben darf, soll seine Zahl nicht in Wolkenwerder
     * verbrauchen. Zwei Werderwelten mit demselben Schluessel waeren EINE Obergrenze
     * fuer beide.
     */
    protected static final String WOLKENWELT = "freimarken:wolkenwerder";

    /** Ein leeres Lager der zu pruefenden Art. */
    protected abstract BesitzLager lager();

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    @Test
    @DisplayName("Eine freie Stelle ist leer, eine vergebene wird gefunden")
    void anlegenUndFinden() {
        BesitzLager lager = lager();
        assertTrue(lager.finde(WELT, 0, 0).isEmpty());

        Parzelle neu = lager.anlegen(WELT, 0, 0, anna, 0L);
        assertEquals(0, neu.x());
        assertEquals(0, neu.z());
        assertEquals(anna, neu.eigentuemer());
        assertTrue(neu.mitbauer().isEmpty());

        Parzelle gefunden = lager.finde(WELT, 0, 0).orElseThrow();
        assertEquals(neu.id(), gefunden.id());
        assertEquals(anna, gefunden.eigentuemer());
    }

    @Test
    @DisplayName("Dieselbe Stelle zweimal zu vergeben ist ein Fehler, kein Ueberschreiben")
    void keinStillesUeberschreiben() {
        BesitzLager lager = lager();
        lager.anlegen(WELT, 1, 1, anna, 0L);
        assertThrows(IllegalStateException.class, () -> lager.anlegen(WELT, 1, 1, bert, 0L));
        // Anna behaelt ihre Parzelle.
        assertEquals(anna, lager.finde(WELT, 1, 1).orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Jede Welt hat ihr eigenes Raster")
    void weltenSindGetrennt() {
        BesitzLager lager = lager();
        lager.anlegen(WELT, 2, 2, anna, 0L);
        assertTrue(lager.finde("freimarken:freistaedte", 2, 2).isEmpty());
        assertEquals(0, lager.anzahlVon("freimarken:freistaedte", anna));
    }

    @Test
    @DisplayName("Eine gegruendete Freistadt traegt ihre Art, ihren Namen und ihr Feld")
    void freistadtGruenden() {
        BesitzLager lager = lager();
        assertTrue(lager.besitzIn(STADTWELT, Art.FREISTADT).isEmpty());

        // Lauter VERSCHIEDENE Werte, besonders feldX != feldZ: Waeren sie gleich, bliebe
        // ein vertauschtes Paar in der Ablage unsichtbar.
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L,
                "neustadt");

        assertEquals(Art.FREISTADT, stadt.art());
        assertEquals("neustadt", stadt.name());
        assertEquals(3, stadt.x());
        assertEquals(-7, stadt.z(), "eine NEGATIVE Feldnummer - Feldgrenze.von() ist -10,"
                + " und eine Ablage, die int als unsigned behandelt, faellt nur hier auf");
        assertEquals(anna, stadt.eigentuemer());
        assertEquals(400_000L, stadt.preis());
        assertEquals(Zustand.AKTIV, stadt.zustand());
        assertTrue(stadt.istFreistadt());
    }

    @Test
    @DisplayName("Jede Freistadt bekommt eine Kontonummer - und sie UEBERLEBT das Lesen")
    void kontonummerEntstehtUndUeberlebt() {
        // JEDE UMSETZUNG VERGIBT DIESE NUMMER SELBST [Teil E1], und darum steht die
        // Zusage hier und nicht in einer einzelnen Probe: Das Speicherlager zieht sie
        // mit UUID.randomUUID, das MariaLager schreibt sie in die Spalte und liest sie
        // zurueck, und Schemaschritt 25 traegt sie fuer bestehende Staedte nach. Eine
        // Umsetzung, die das vergisst, wirft nicht - Parzelle wirft, und zwar erst beim
        // LESEN, also nach einem Neustart und nicht beim Gruenden.
        BesitzLager lager = lager();
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L,
                "kassenstadt");

        assertNotNull(stadt.kasse(), "schon der Rueckgabewert traegt sie");

        // Ueber finde() GELESEN: der Weg, den das Spiel nach einem Neustart nimmt. Hier
        // faellt eine Umsetzung auf, die die Nummer schreibt und beim Lesen vergisst -
        // dann liegt das Guthaben unter einer Nummer, die niemand mehr kennt.
        Parzelle gelesen = lager.finde(STADTWELT, 3, -7).orElseThrow();
        assertEquals(stadt.kasse(), gelesen.kasse(),
                "und nach dem Weg durch die Ablage ist es DIESELBE. Eine Nummer, die"
                        + " sich beim Lesen aendert, macht das Geld unerreichbar - und"
                        + " keine Zeile im Buch erzaehlt davon.");
    }

    @Test
    @DisplayName("Zwei Staedte bekommen VERSCHIEDENE Kontonummern")
    void zweiStaedteZweiKontonummern() {
        // Die Zusage, auf der die Trennung des Geldes ruht. Eine Umsetzung, die eine
        // feste Nummer vergibt - oder sie aus der id rechnet und dabei etwas vertauscht
        // -, haette ein Sammelkonto fuer alle Staedte, und jede saehe das Geld der
        // anderen.
        BesitzLager lager = lager();
        Parzelle eine = lager.freistadtAnlegen(STADTWELT, 1, 2, anna, 400_000L,
                "erststadt");
        Parzelle andere = lager.freistadtAnlegen(STADTWELT, 4, 5, anna, 400_000L,
                "zweitstadt");

        assertNotEquals(eine.kasse(), andere.kasse());
    }

    @Test
    @DisplayName("Eine gewoehnliche Parzelle bekommt KEINE Kontonummer")
    void parzelleOhneKontonummer() {
        // Die andere Richtung, und sie ist nicht nur Ordnungsliebe: Eine Parzelle mit
        // Konto waere ein Konto, auf das nie etwas fliesst - und ein Betreiber, der es
        // in der Spalte findet, sucht nach einer Kasse, die es nicht gibt. Parzelle
        // weist diese Zeile ab, also faellt eine Umsetzung, die hier etwas einsetzt,
        // sofort auf.
        BesitzLager lager = lager();
        Parzelle eine = lager.anlegen(WELT, 0, 0, anna, 0L);

        assertEquals(Parzelle.KEINE_KASSE, eine.kasse());
        assertEquals(Parzelle.KEINE_KASSE,
                lager.finde(WELT, 0, 0).orElseThrow().kasse(),
                "auch nach dem Weg durch die Ablage - eine Umsetzung, die die Spalte"
                        + " beim Lesen mit einem Ersatzwert fuellt, faellt hier auf");
    }

    // --- Die Meerinseln [Schritt 10] -------------------------------------------

    @Test
    @DisplayName("Eine Insel wird genommen und gefunden - als art WERDER")
    void werderAnlegenUndFinden() {
        BesitzLager lager = lager();
        assertTrue(lager.finde(INSELWELT, 2, -3).isEmpty());

        Parzelle insel = lager.werderAnlegen(INSELWELT, 2, -3, anna, 75_000L);

        assertEquals(2, insel.x());
        assertEquals(-3, insel.z());
        assertEquals(anna, insel.eigentuemer());
        assertEquals(75_000L, insel.preis());
        assertTrue(insel.istWerder(), "die Art steht in der Zeile und nicht nur im Buch");
        assertEquals(Zustand.AKTIV, insel.zustand());

        Parzelle gefunden = lager.finde(INSELWELT, 2, -3).orElseThrow();
        assertEquals(insel.id(), gefunden.id());
        assertTrue(gefunden.istWerder(),
                "auch nach dem Weg durch die Ablage - eine Umsetzung, die die Art beim"
                        + " Lesen auf parzelle zurueckfallen laesst, faellt hier auf");
    }

    @Test
    @DisplayName("Eine Insel hat KEINEN Namen, KEINE Kasse, KEINE Regeln, KEINEN Besuch")
    void werderHatKeineStadtspalten() {
        // Die vier Spalten, die nur eine Freistadt fuellt. Eine Insel ist Eigentum und
        // keine Siedlung; eine Kontonummer an ihr waere ein Konto, auf das nie etwas
        // fliesst, und ein Besuchstag ein Tag, den niemand liest.
        BesitzLager lager = lager();
        Parzelle insel = lager.werderAnlegen(INSELWELT, 0, 0, anna, 75_000L);

        assertFalse(insel.istFreistadt());
        assertEquals(Parzelle.KEIN_NAME, insel.name());
        assertEquals(Parzelle.KEINE_KASSE, insel.kasse());
        assertEquals(Parzelle.KEINE_REGELN, insel.regeln());
        assertEquals(Parzelle.NIE_BESUCHT, insel.besucht());

        Parzelle gelesen = lager.finde(INSELWELT, 0, 0).orElseThrow();
        assertEquals(Parzelle.KEIN_NAME, gelesen.name());
        assertEquals(Parzelle.KEINE_KASSE, gelesen.kasse());
        assertEquals(Parzelle.KEINE_REGELN, gelesen.regeln());
        assertEquals(Parzelle.NIE_BESUCHT, gelesen.besucht());
    }

    @Test
    @DisplayName("Ein belegtes Inselfeld laesst sich nicht zweimal nehmen")
    void werderNurEinmal() {
        // Der Riegel gehoert der ABLAGE und nicht dem Aufrufer: Eine Pruefung davor
        // haette zwischen Fragen und Schreiben eine Luecke, und zwei Spieler, die
        // gleichzeitig tippen, bekaemen dasselbe Feld - beide haetten gezahlt.
        BesitzLager lager = lager();
        lager.werderAnlegen(INSELWELT, 5, 5, anna, 75_000L);

        assertThrows(IllegalStateException.class,
                () -> lager.werderAnlegen(INSELWELT, 5, 5, bert, 75_000L));
        assertEquals(anna, lager.finde(INSELWELT, 5, 5).orElseThrow().eigentuemer(),
                "und die erste Insel steht unveraendert");
    }

    @Test
    @DisplayName("Eine Insel ZAEHLT fuer ihren Besitzer - das Stadtverzeichnis kennt sie nicht")
    void werderZaehltAberIstKeineStadt() {
        // Zwei Zusagen in einer Probe, weil sie dieselbe Sache von zwei Seiten sind:
        // Eine Insel liegt auf einem Raster, also zaehlt sie bei anzahlVon mit - daran
        // haengt die Hoechstzahl je Rang. Sie ist aber keine Stadt, also darf besitzIn
        // sie nicht nennen - sonst erschiene sie in /warp freistadt und in jeder Liste.
        BesitzLager lager = lager();
        lager.werderAnlegen(INSELWELT, 1, 1, anna, 75_000L);
        lager.werderAnlegen(INSELWELT, 2, 1, anna, 75_000L);

        assertEquals(2, lager.anzahlVon(INSELWELT, anna));
        assertEquals(2, lager.vonSpieler(INSELWELT, anna).size());
        assertEquals(0, lager.anzahlVon(INSELWELT, bert));
        assertTrue(lager.besitzIn(INSELWELT, Art.FREISTADT).isEmpty(),
                "eine Insel ist keine Freistadt - besitzIn fragt die ART und nicht die"
                        + " Lage");
        assertEquals(0, lager.anzahlVon(WELT, anna),
                "und in der Marktstadt zaehlt sie gar nicht mit - sonst koestete die"
                        + " naechste Parzelle das Doppelte");
    }

    @Test
    @DisplayName("Die vier Stadtsetzer weisen eine Insel AB")
    void stadtsetzerWeisenEineInselAb() {
        // Dieselbe Zusage wie fuer eine Marktstadtparzelle, und sie ist hier NEU zu
        // pruefen: Die Riegel fragen "ist Freistadt", und eine Insel ist die erste Art,
        // die weder das eine noch das andere ist. Ein stilles Hinnehmen waere eine
        // Einstellung, die der Betreiber setzt und die lautlos nichts tut.
        BesitzLager lager = lager();
        Parzelle insel = lager.werderAnlegen(INSELWELT, 7, 7, anna, 75_000L);

        assertThrows(RuntimeException.class,
                () -> lager.regelnSetzen(insel.id(), "kein Feuer"));
        assertThrows(RuntimeException.class,
                () -> lager.besuchSetzen(insel.id(), LocalDate.of(2026, 11, 18)));
        assertThrows(RuntimeException.class,
                () -> lager.buergerrechteSetzen(insel.id(), Set.of(Tat.BAUEN)));
        assertThrows(RuntimeException.class,
                () -> lager.zustandSetzen(insel.id(), Zustand.VERFALLEN));
    }

    @Test
    @DisplayName("Mitbauer gelten auch auf einer Insel - und sie UEBERLEBEN")
    void mitbauerAufDerInsel() {
        // Die Gegenrichtung zur Probe darueber: Mitbauer haengen am Datensatz Besitz und
        // gelten fuer JEDE Art. Ohne diese Zusage waere eine Insel ein Grundstueck, auf
        // dem niemand mitbauen kann - und das waere der einzige Besitz im Spiel ohne
        // diese Moeglichkeit.
        BesitzLager lager = lager();
        Parzelle insel = lager.werderAnlegen(INSELWELT, -4, 8, anna, 75_000L);

        lager.mitbauerSetzen(insel.id(), Set.of(bert));

        assertEquals(Set.of(bert),
                lager.finde(INSELWELT, -4, 8).orElseThrow().mitbauer());
        assertTrue(lager.finde(INSELWELT, -4, 8).orElseThrow().istWerder(),
                "und die Art bleibt, was sie war - eine Umsetzung, die die Zeile beim"
                        + " Mitbauer-Setzen neu zusammenbaut, verliert sie hier");
    }

    @Test
    @DisplayName("Eine Insel laesst sich freigeben")
    void werderEntfernen() {
        BesitzLager lager = lager();
        Parzelle insel = lager.werderAnlegen(INSELWELT, 3, 3, anna, 75_000L);

        lager.entfernen(insel.id());

        assertTrue(lager.finde(INSELWELT, 3, 3).isEmpty());
        assertEquals(0, lager.anzahlVon(INSELWELT, anna));
    }

    // --- Die Wolkeninseln [Schritt 11] -----------------------------------------

    @Test
    @DisplayName("Eine Wolkeninsel wird genommen und gefunden - als art WOLKE")
    void wolkeAnlegenUndFinden() {
        BesitzLager lager = lager();
        assertTrue(lager.finde(WOLKENWELT, 2, -3).isEmpty());

        Parzelle insel = lager.wolkeAnlegen(WOLKENWELT, 2, -3, anna, 0L);

        assertEquals(2, insel.x());
        assertEquals(-3, insel.z());
        assertEquals(anna, insel.eigentuemer());
        assertEquals(0L, insel.preis(), "gratis - Plan 6.2, und 0 ist kein Sonderfall");
        assertTrue(insel.istWolke(), "die Art steht in der Zeile und nicht nur im Buch");
        assertFalse(insel.istWerder(), "und sie ist KEINE Meerinsel");
        assertEquals(Zustand.AKTIV, insel.zustand());

        Parzelle gefunden = lager.finde(WOLKENWELT, 2, -3).orElseThrow();
        assertEquals(insel.id(), gefunden.id());
        assertTrue(gefunden.istWolke(),
                "auch nach dem Weg durch die Ablage - eine Umsetzung, die die Art beim"
                        + " Lesen auf parzelle zurueckfallen laesst, faellt hier auf");
    }

    @Test
    @DisplayName("besitzIn trennt die Arten - und gibt sie in zugesagter Reihenfolge")
    void besitzInTrenntDieArten() {
        // DIE PROBE, DIE ES BEI DEN MEERINSELN NICHT GAB, und sie ist der Grund fuer die
        // eigene Art: In einer leeren Welt kann niemand auf dem Feld STEHEN, das er haben
        // will - das Buch sucht das naechste freie aus der Liste der vergebenen. Diese
        // Liste darf weder Meerinseln noch Freistaedte enthalten, sonst gilt ein Feld als
        // belegt, das frei ist.
        BesitzLager lager = lager();
        lager.wolkeAnlegen(WOLKENWELT, 0, 0, anna, 0L);
        lager.wolkeAnlegen(WOLKENWELT, 1, 0, bert, 0L);
        lager.werderAnlegen(INSELWELT, 0, 0, anna, 75_000L);
        lager.freistadtAnlegen(STADTWELT, 0, 0, anna, 400_000L, "steinburg");

        assertEquals(2, lager.besitzIn(WOLKENWELT, Art.WOLKE).size());
        assertEquals(1, lager.besitzIn(INSELWELT, Art.WERDER).size());
        assertEquals(1, lager.besitzIn(STADTWELT, Art.FREISTADT).size());
        assertTrue(lager.besitzIn(WOLKENWELT, Art.WERDER).isEmpty(),
                "die Art UND die Welt zaehlen - eine Wolkeninsel ist keine Meerinsel");
        assertTrue(lager.besitzIn(WOLKENWELT, Art.FREISTADT).isEmpty());
        assertTrue(lager.besitzIn(INSELWELT, Art.WOLKE).isEmpty(),
                "und in Hafenwerder liegt keine Wolkeninsel");

        // DIE REIHENFOLGE IST ZUGESAGT, und bei einer namenlosen Art heisst das: nach
        // dem Alter. Ohne die zweite Sortierspalte gibt MariaDB die Zeilen in beliebiger
        // Folge - und eine Zuteilung, die ihr folgt, naehme zweimal hintereinander ein
        // anderes Feld.
        var felder = lager.besitzIn(WOLKENWELT, Art.WOLKE);
        assertEquals(0, felder.get(0).x(), "die zuerst genommene zuerst");
        assertEquals(1, felder.get(1).x());
        assertTrue(felder.get(0).id() < felder.get(1).id());
    }

    @Test
    @DisplayName("Eine Wolkeninsel hat KEINEN Namen, KEINE Kasse, KEINE Regeln")
    void wolkeHatKeineStadtspalten() {
        // Die vier Spalten, die nur eine Freistadt fuellt - dieselbe Zusage wie fuer eine
        // Meerinsel, und sie muss hier NEU geprueft werden: Es ist eine andere Art, und
        // die Umsetzungen fuehren sie als LITERAL im INSERT.
        BesitzLager lager = lager();
        Parzelle insel = lager.wolkeAnlegen(WOLKENWELT, 0, 0, anna, 0L);

        assertFalse(insel.istFreistadt());
        assertEquals(Parzelle.KEIN_NAME, insel.name());
        assertEquals(Parzelle.KEINE_KASSE, insel.kasse());
        assertEquals(Parzelle.KEINE_REGELN, insel.regeln());
        assertEquals(Parzelle.NIE_BESUCHT, insel.besucht());

        Parzelle gelesen = lager.finde(WOLKENWELT, 0, 0).orElseThrow();
        assertEquals(Parzelle.KEIN_NAME, gelesen.name());
        assertEquals(Parzelle.KEINE_KASSE, gelesen.kasse());
        assertEquals(Parzelle.KEINE_REGELN, gelesen.regeln());
        assertEquals(Parzelle.NIE_BESUCHT, gelesen.besucht());
    }

    @Test
    @DisplayName("Ein belegtes Wolkenfeld laesst sich nicht zweimal nehmen")
    void wolkeNurEinmal() {
        // Der Riegel gehoert der ABLAGE und nicht dem Aufrufer, und hier traegt er mehr
        // als bei den Meerinseln: Zwei Spieler, die gleichzeitig /wolke nehmen tippen,
        // bekaemen dasselbe Feld - und auf eines von beiden wuerde zweimal eine
        // Startinsel AUFGESETZT, die zweite ueber die Bauten der ersten.
        BesitzLager lager = lager();
        lager.wolkeAnlegen(WOLKENWELT, 5, 5, anna, 0L);

        assertThrows(IllegalStateException.class,
                () -> lager.wolkeAnlegen(WOLKENWELT, 5, 5, bert, 0L));
        assertEquals(anna, lager.finde(WOLKENWELT, 5, 5).orElseThrow().eigentuemer(),
                "und die erste Insel steht unveraendert");
    }

    @Test
    @DisplayName("Eine Wolkeninsel ZAEHLT fuer ihren Besitzer - in IHRER Welt")
    void wolkeZaehltInIhrerWelt() {
        BesitzLager lager = lager();
        lager.wolkeAnlegen(WOLKENWELT, 1, 1, anna, 0L);
        lager.wolkeAnlegen(WOLKENWELT, 2, 1, anna, 0L);

        assertEquals(2, lager.anzahlVon(WOLKENWELT, anna));
        assertEquals(2, lager.vonSpieler(WOLKENWELT, anna).size());
        assertEquals(0, lager.anzahlVon(WOLKENWELT, bert));
        assertEquals(0, lager.anzahlVon(WELT, anna),
                "und in der Marktstadt zaehlt sie gar nicht mit - sonst koestete die"
                        + " naechste Parzelle das Doppelte");
        assertEquals(0, lager.anzahlVon(INSELWELT, anna),
                "und auch nicht in Hafenwerder - wer eine Meerinsel darf, verbraucht"
                        + " seine Zahl nicht in Wolkenwerder");
    }

    @Test
    @DisplayName("Die vier Stadtsetzer weisen eine Wolkeninsel AB")
    void stadtsetzerWeisenEineWolkeAb() {
        // Dieselbe Zusage wie fuer eine Meerinsel, und sie ist NEU zu pruefen: Die
        // Riegel fragen "ist Freistadt", und ein Riegel, den nie etwas ausloest, ist
        // gruen mit UND ohne. Ein stilles Hinnehmen waere eine Einstellung, die der
        // Betreiber setzt und die lautlos nichts tut.
        BesitzLager lager = lager();
        Parzelle insel = lager.wolkeAnlegen(WOLKENWELT, 7, 7, anna, 0L);

        assertThrows(RuntimeException.class,
                () -> lager.regelnSetzen(insel.id(), "kein Feuer"));
        assertThrows(RuntimeException.class,
                () -> lager.besuchSetzen(insel.id(), LocalDate.of(2026, 11, 18)));
        assertThrows(RuntimeException.class,
                () -> lager.buergerrechteSetzen(insel.id(), Set.of(Tat.BAUEN)));
        assertThrows(RuntimeException.class,
                () -> lager.zustandSetzen(insel.id(), Zustand.VERFALLEN));
    }

    @Test
    @DisplayName("Mitbauer gelten auch auf einer Wolkeninsel - und sie UEBERLEBEN")
    void mitbauerAufDerWolke() {
        BesitzLager lager = lager();
        Parzelle insel = lager.wolkeAnlegen(WOLKENWELT, -4, 8, anna, 0L);

        lager.mitbauerSetzen(insel.id(), Set.of(bert));

        assertEquals(Set.of(bert),
                lager.finde(WOLKENWELT, -4, 8).orElseThrow().mitbauer());
        assertTrue(lager.finde(WOLKENWELT, -4, 8).orElseThrow().istWolke(),
                "und die Art bleibt, was sie war - eine Umsetzung, die die Zeile beim"
                        + " Mitbauer-Setzen neu zusammenbaut, verliert sie hier");
    }

    @Test
    @DisplayName("Eine Wolkeninsel laesst sich freigeben")
    void wolkeEntfernen() {
        BesitzLager lager = lager();
        Parzelle insel = lager.wolkeAnlegen(WOLKENWELT, 3, 3, anna, 0L);

        lager.entfernen(insel.id());

        assertTrue(lager.finde(WOLKENWELT, 3, 3).isEmpty());
        assertEquals(0, lager.anzahlVon(WOLKENWELT, anna));
        assertTrue(lager.besitzIn(WOLKENWELT, Art.WOLKE).isEmpty(),
                "und das Feld ist wieder zu vergeben");
    }

    @Test
    @DisplayName("Regeln werden gesetzt, ersetzt, geloescht - und sie UEBERLEBEN")
    void regelnSetzenUndLesen() {
        // Dieselbe Zusage wie bei den Buergerrechten, und derselbe lautlose Fehler
        // droht: Eine Umsetzung, die den Text schreibt und beim Lesen vergisst, wirft
        // nicht - sie liefert eine Stadt ohne Regeln. Im Spiel heisst das: Der Besitzer
        // setzt seine Regeln, sie stehen in der Datenbank, und nach dem naechsten
        // Neustart hat seine Stadt keinen Teleporter mehr.
        BesitzLager lager = lager();
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L,
                "regelstadt");
        assertEquals(Parzelle.KEINE_REGELN, stadt.regeln(),
                "ab Werk hat eine Stadt keine Regeln - der Anfangszustand, an dem die"
                        + " Auflage haengt");
        assertFalse(stadt.hatRegeln());

        lager.regelnSetzen(stadt.id(), "Kein PvP. Baue nicht an fremde Haeuser.");

        // Ueber finde() GELESEN - der Weg nach einem Neustart.
        Parzelle danach = lager.finde(STADTWELT, 3, -7).orElseThrow();
        assertEquals("Kein PvP. Baue nicht an fremde Haeuser.", danach.regeln());
        assertTrue(danach.hatRegeln());

        // ERSETZEND und nicht ergaenzend: Der neue Text ist der ganze Text.
        lager.regelnSetzen(stadt.id(), "Nur noch eine Regel.");
        assertEquals("Nur noch eine Regel.",
                lager.finde(STADTWELT, 3, -7).orElseThrow().regeln(),
                "der alte Text ist WEG - ein Anhaengen waere eine Regel, die der"
                        + " Besitzer zurueckgezogen hat und die bleibt");

        // Und der Weg zurueck in den Anfangszustand. Ohne ihn koennte ein
        // Stadtbesitzer eine Regel aufstellen und nie wieder loswerden.
        lager.regelnSetzen(stadt.id(), null);
        Parzelle leer = lager.finde(STADTWELT, 3, -7).orElseThrow();
        assertEquals(Parzelle.KEINE_REGELN, leer.regeln());
        assertFalse(leer.hatRegeln(), "und damit ist die Auflage wieder offen");
    }

    @Test
    @DisplayName("Der Besuchstag wird gesetzt, ersetzt, geloescht - und er UEBERLEBT")
    void besuchSetzenUndLesen() {
        // DERSELBE LAUTLOSE FEHLER droht wie bei den Regeln: Eine Umsetzung, die den Tag
        // schreibt und beim Lesen vergisst, wirft nicht - sie liefert eine Stadt, die
        // nie besucht wurde. Im Spiel heisst das: Der Verfallslauf raeumt nach einem
        // Jahr eine Stadt ab, in der jeden Tag jemand stand.
        BesitzLager lager = lager();
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L,
                "besuchsstadt");
        assertEquals(Parzelle.NIE_BESUCHT, stadt.besucht(),
                "ab Werk war noch niemand da - und das ist NICHT 1970: Die Frist laeuft"
                        + " ab der Gruendung, und die steht in erworbenAm");

        lager.besuchSetzen(stadt.id(), LocalDate.of(2026, 11, 17));

        // Ueber finde() GELESEN - der Weg nach einem Neustart.
        Parzelle danach = lager.finde(STADTWELT, 3, -7).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 17), danach.besucht());

        // ERSETZEND: Der neue Tag ist der ganze Tag. Ein Lager, das den aelteren
        // behielte, liesse eine besuchte Stadt verfallen.
        lager.besuchSetzen(stadt.id(), LocalDate.of(2026, 11, 18));
        assertEquals(LocalDate.of(2026, 11, 18),
                lager.finde(STADTWELT, 3, -7).orElseThrow().besucht());

        // Und der Weg zurueck auf "noch nie" - ein Setzer, der nur setzen und nie
        // loeschen kann, ist die Haelfte eines Setzers.
        lager.besuchSetzen(stadt.id(), null);
        assertEquals(Parzelle.NIE_BESUCHT,
                lager.finde(STADTWELT, 3, -7).orElseThrow().besucht());
    }

    @Test
    @DisplayName("Ein Besuchstag an einer gewoehnlichen Parzelle wird ABGEWIESEN")
    void besuchNurFuerStaedte() {
        // Dieselbe Zusage wie bei den Regeln, und sie haelt die Spalte sauber: Ein Tag
        // an einer Marktstadtzeile waere ein Wert, den niemand je liest - gelesen wird
        // er allein von der Verfallsregel, und die gilt nur fuer Staedte.
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 2, 3, anna, 1_000L);

        assertThrows(IllegalStateException.class,
                () -> lager.besuchSetzen(parzelle.id(), LocalDate.of(2026, 11, 17)));
    }

    @Test
    @DisplayName("Ein Besuchstag fuer eine Nummer, die es nicht gibt, wird ABGEWIESEN")
    void besuchOhneParzelle() {
        BesitzLager lager = lager();

        assertThrows(IllegalStateException.class,
                () -> lager.besuchSetzen(4711L, LocalDate.of(2026, 11, 17)));
    }

    @Test
    @DisplayName("LEERRAUM wird zu KEINE_REGELN - in jeder Umsetzung gleich")
    void leerraumWirdKeineRegeln() {
        // DIE NAHT ZWISCHEN DEN FASSUNGEN: Ohne diese Zusage traegt die eine Fassung
        // einen Text aus drei Leerzeichen und die andere ein NULL - und
        // Parzelle.hatRegeln bekaeme auf dieselbe Frage zwei Antworten. Im Spiel waere
        // das eine Stadt, die auf dem Probeserver ihren Teleporter hat und auf dem
        // echten nicht.
        BesitzLager lager = lager();
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L,
                "leerstadt");
        lager.regelnSetzen(stadt.id(), "etwas");

        lager.regelnSetzen(stadt.id(), "   ");

        Parzelle danach = lager.finde(STADTWELT, 3, -7).orElseThrow();
        assertEquals(Parzelle.KEINE_REGELN, danach.regeln());
        assertFalse(danach.hatRegeln(),
                "drei Leerzeichen sind keine Regel - ohne diese Zeile waere sie eine"
                        + " erfuellte Auflage");
    }

    @Test
    @DisplayName("Zweimal DERSELBE Text ist kein Fehler")
    void zweimalDerselbeText() {
        // Gegen eine Umsetzung, die betroffene Zeilen zaehlt und bei 0 klagt: MariaDB
        // meldet bei executeUpdate die GETROFFENEN Zeilen, nicht die geaenderten - aber
        // das ist eine Annahme ueber den Treiber, und sie gehoert gemessen.
        BesitzLager lager = lager();
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L,
                "gleichstadt");
        lager.regelnSetzen(stadt.id(), "Kein PvP.");

        lager.regelnSetzen(stadt.id(), "Kein PvP.");

        assertEquals("Kein PvP.",
                lager.finde(STADTWELT, 3, -7).orElseThrow().regeln());
    }

    @Test
    @DisplayName("Regeln gibt es nur fuer eine FREISTADT - und nur fuer bekannte Nummern")
    void regelnNurFuerStaedte() {
        BesitzLager lager = lager();
        Parzelle eine = lager.anlegen(WELT, 0, 0, anna, 0L);

        var keineStadt = assertThrows(IllegalStateException.class,
                () -> lager.regelnSetzen(eine.id(), "Kein PvP."));
        assertTrue(keineStadt.getMessage().contains("keine Freistadt"),
                keineStadt.getMessage());

        // Und eine Nummer, die es nicht gibt. Die ZWEI Klagen muessen sich
        // unterscheiden: Sonst sucht ein Betreiber bei einer fehlenden Zeile nach einer
        // falschen Art.
        var unbekannt = assertThrows(IllegalStateException.class,
                () -> lager.regelnSetzen(999_999L, "Kein PvP."));
        assertTrue(unbekannt.getMessage().contains("Keine Parzelle"),
                unbekannt.getMessage());
    }

    @Test
    @DisplayName("Buergerrechte werden gesetzt, ersetzt, geleert - und sie UEBERLEBEN")
    void buergerrechteSetzenUndLesen() {
        // DER RIEGEL GEGEN DEN LAUTLOSEN VERLUST. Die Rechte liegen in einer eigenen
        // Tabelle und muessen beim Lesen der Zeile mitgeholt werden. Eine Umsetzung, die
        // das vergisst, wirft nicht - sie liefert eine Stadt mit leeren Rechten. Im Spiel
        // heisst das: Der Betreiber stellt etwas ein, es steht in der Datenbank, und nach
        // dem naechsten Neustart gilt es nicht mehr. Kein anderer Probenlauf saehe das.
        BesitzLager lager = lager();
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L,
                "rechtestadt");
        assertEquals(Parzelle.KEINE_RECHTE, stadt.buergerrechte(),
                "ab Werk gibt eine Stadt nichts her - die sichere Richtung");

        lager.buergerrechteSetzen(stadt.id(), Set.of(Tat.BENUTZEN, Tat.BEHAELTER));

        // Ueber finde() GELESEN und nicht am Rueckgabewert gemessen: Der Weg durch die
        // Ablage und zurueck ist der, den das Spiel nach einem Neustart nimmt.
        Parzelle danach = lager.finde(STADTWELT, 3, -7).orElseThrow();
        assertEquals(Set.of(Tat.BENUTZEN, Tat.BEHAELTER), danach.buergerrechte());
        assertTrue(danach.buergerDuerfen(Tat.BENUTZEN), "und die Frage an der Zeile");
        assertTrue(danach.buergerDuerfen(Tat.BEHAELTER));
        assertFalse(danach.buergerDuerfen(Tat.BAUEN),
                "ZWEI von drei gesetzt, und das dritte darf nicht mitkommen - mit allen"
                        + " drei waere eine Umsetzung, die einfach jedes Recht gibt,"
                        + " nicht zu unterscheiden");

        // ERSETZEND und nicht ergaenzend: Die neue Menge ist die ganze Menge.
        lager.buergerrechteSetzen(stadt.id(), Set.of(Tat.BAUEN));
        assertEquals(Set.of(Tat.BAUEN),
                lager.finde(STADTWELT, 3, -7).orElseThrow().buergerrechte(),
                "die beiden alten sind WEG - ein Ergaenzen waere ein Recht, das der"
                        + " Betreiber wegnimmt und das bleibt");

        // Und das Leeren, der Weg zurueck in den Zustand ab Werk.
        lager.buergerrechteSetzen(stadt.id(), Parzelle.KEINE_RECHTE);
        assertEquals(Parzelle.KEINE_RECHTE,
                lager.finde(STADTWELT, 3, -7).orElseThrow().buergerrechte());
    }

    @Test
    @DisplayName("Buergerrechte aendern SONST NICHTS an der Zeile")
    void buergerrechteAendernSonstNichts() {
        // Dieselbe Zusage wie bei den Mitbauern, und aus demselben Fund: Am 11.11.2026
        // verlor eine Zeile beim Mitbauer-Setzen still ihren Namen, weil eine Umsetzung
        // sie von Hand zusammensetzte. Eine Freistadt OHNE Namen wirft - das fiel auf.
        // Ein verlorener Eigentuemer oder ein verlorener Preis faellt NICHT auf.
        BesitzLager lager = lager();
        Parzelle vorher = lager.freistadtAnlegen(STADTWELT, 4, -9, anna, 400_000L,
                "bleibtstadt");
        lager.mitbauerSetzen(vorher.id(), Set.of(bert));
        Parzelle mitMitbauer = lager.finde(STADTWELT, 4, -9).orElseThrow();

        lager.buergerrechteSetzen(vorher.id(), Set.of(Tat.BAUEN));

        Parzelle danach = lager.finde(STADTWELT, 4, -9).orElseThrow();
        assertEquals(mitMitbauer.id(), danach.id());
        assertEquals(mitMitbauer.name(), danach.name(), "der Name bleibt");
        assertEquals(mitMitbauer.eigentuemer(), danach.eigentuemer());
        assertEquals(Set.of(bert), danach.mitbauer(), "und die Mitbauer bleiben");
        assertEquals(mitMitbauer.preis(), danach.preis());
        assertEquals(mitMitbauer.erworbenAm(), danach.erworbenAm());
        assertEquals(mitMitbauer.art(), danach.art());
        assertEquals(mitMitbauer.zustand(), danach.zustand());
        assertEquals(mitMitbauer.elternId(), danach.elternId());

        // Und die Gegenrichtung: Mitbauer zu setzen darf die Rechte nicht mitnehmen.
        // Genau das tat der Nachbau in SpeicherLager, bis er zu mitMitbauern wurde.
        lager.mitbauerSetzen(vorher.id(), Set.of());
        assertEquals(Set.of(Tat.BAUEN),
                lager.finde(STADTWELT, 4, -9).orElseThrow().buergerrechte(),
                "nach einer Mitbauer-Aenderung muessen die Buergerrechte noch"
                        + " dastehen - der Nachbau einer Zeile von Hand verliert genau"
                        + " hier das jeweils neueste Feld");
    }

    @Test
    @DisplayName("Buergerrechte auf etwas, das keine Freistadt ist - und auf nichts")
    void buergerrechteNurFuerStaedte() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 0, 0, anna, 0L);

        assertThrows(IllegalStateException.class,
                () -> lager.buergerrechteSetzen(parzelle.id(), Set.of(Tat.BAUEN)),
                "eine Marktstadtparzelle hat keine Buerger - ein stilles Hinnehmen"
                        + " waere eine Einstellung, die lautlos nichts tut");
        assertThrows(IllegalStateException.class,
                () -> lager.buergerrechteSetzen(999_999L, Set.of(Tat.BAUEN)),
                "und eine Nummer, die es nicht gibt, ist ein Fehler und keine Nulltat");

        // AUCH DIE LEERE MENGE muss klagen, und das ist der Fall, der leicht durchfaellt:
        // Wer nur das Einfuegen schreibt, fuegt bei einer leeren Menge gar nichts ein -
        // und dann faellt kein Fremdschluessel auf, weil keine Zeile entsteht.
        assertThrows(IllegalStateException.class,
                () -> lager.buergerrechteSetzen(999_999L, Parzelle.KEINE_RECHTE),
                "eine leere Menge auf eine unbekannte Nummer ist derselbe Fehler -"
                        + " nur faellt dabei kein Fremdschluessel auf");
    }

    @Test
    @DisplayName("Eine Freistadt ist ueber finde() erreichbar - sonst schuetzt sie niemand")
    void freistadtLiegtAufDemFeld() {
        // Die tragende Zusage des ganzen Entwurfs: Weil die Stadt in derselben Tabelle
        // und auf einer Feldnummer liegt, findet die Schutzwache sie mit DERSELBEN
        // Punktabfrage wie eine Parzelle. Gaebe es dafuer einen zweiten Weg, muesste sie
        // bei jedem gesetzten Block zwei Orte fragen.
        BesitzLager lager = lager();
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L, "hain");

        Parzelle gefunden = lager.finde(STADTWELT, 3, -7).orElseThrow();
        assertEquals(stadt.id(), gefunden.id());
        assertEquals("hain", gefunden.name());
        assertEquals(Art.FREISTADT, gefunden.art());
        assertTrue(gefunden.darfBauen(anna));
        assertFalse(gefunden.darfBauen(bert));
    }

    @Test
    @DisplayName("Eine Freistadt behaelt ihren Namen, wenn Mitbauer gesetzt werden")
    void freistadtBehaeltNamenBeiMitbauern() {
        // FUND vom 11.11.2026, und ein Fehler mit Ansage: SpeicherLager baute die Zeile
        // ueber den VERKUERZTEN Konstruktor neu, der KEIN_NAME setzt - und eine
        // Freistadt ohne Namen wirft. Ein Stadtbesitzer konnte damit keinen einzigen
        // Mitbauer eintragen. MariaLager traf es nicht, weil es nur die Nebentabelle
        // schreibt; genau darum steht die Probe im VERTRAG und nicht bei einer
        // Umsetzung - sonst haette sie den Fehler nur in einer von drei gesucht.
        BesitzLager lager = lager();
        Parzelle stadt = lager.freistadtAnlegen(STADTWELT, 3, -7, anna, 400_000L,
                "annastadt");

        lager.mitbauerSetzen(stadt.id(), Set.of(bert));

        Parzelle danach = lager.finde(STADTWELT, 3, -7).orElseThrow();
        assertEquals("annastadt", danach.name(), "der Name ueberlebt die Aenderung");
        assertEquals(Art.FREISTADT, danach.art(), "und die Art auch");
        assertEquals(Set.of(bert), danach.mitbauer());
        assertTrue(danach.darfBauen(bert), "der Mitbauer darf jetzt in der Stadt bauen");
        assertTrue(danach.darfBauen(anna), "die Eigentuemerin weiter auch");
    }

    @Test
    @DisplayName("Dasselbe Stadtfeld zweimal zu vergeben ist ein Fehler")
    void keinZweitesStadtfeld() {
        BesitzLager lager = lager();
        lager.freistadtAnlegen(STADTWELT, 1, 2, anna, 400_000L, "erste");

        assertThrows(IllegalStateException.class, () -> lager.freistadtAnlegen(
                STADTWELT, 1, 2, bert, 400_000L, "zweite"));
        assertEquals(anna, lager.finde(STADTWELT, 1, 2).orElseThrow().eigentuemer());
        assertEquals(1, lager.besitzIn(STADTWELT, Art.FREISTADT).size());
    }

    @Test
    @DisplayName("Denselben Stadtnamen zweimal zu vergeben ist ein Fehler")
    void keinZweiterName() {
        // Der Riegel der ABLAGE und keine Pruefung des Aufrufers: Zwischen "ist der Name
        // frei?" und dem Schreiben passt ein zweiter Spieler. Beide Umsetzungen muessen
        // dieselbe Ausnahme werfen, sonst haelt der Vertrag nur in einer.
        BesitzLager lager = lager();
        lager.freistadtAnlegen(STADTWELT, 1, 2, anna, 400_000L, "doppelt");

        assertThrows(IllegalStateException.class, () -> lager.freistadtAnlegen(
                STADTWELT, 4, 5, bert, 400_000L, "doppelt"));
        assertTrue(lager.finde(STADTWELT, 4, 5).isEmpty(),
                "und das Feld bleibt frei - ein halb angelegter Besitz waere schlimmer"
                        + " als gar keiner");
        assertEquals(1, lager.besitzIn(STADTWELT, Art.FREISTADT).size());
    }

    @Test
    @DisplayName("Eine Parzelle bekommt KEINEN Namen - und blockiert damit keinen")
    void parzellenHabenKeinenNamen() {
        // Zwei namenlose Parzellen nebeneinander: Traegt die Spalte eine leere
        // Zeichenkette statt NULL, laeuft die zweite in den eindeutigen Schluessel.
        BesitzLager lager = lager();
        Parzelle eine = lager.anlegen(WELT, 7, 7, anna, 0L);
        Parzelle andere = lager.anlegen(WELT, 8, 8, bert, 0L);

        assertEquals(Parzelle.KEIN_NAME, eine.name());
        assertEquals(Parzelle.KEIN_NAME, andere.name());
        assertEquals(Parzelle.KEIN_NAME, lager.finde(WELT, 7, 7).orElseThrow().name());
    }

    @Test
    @DisplayName("Die Staedteliste kommt nach Namen sortiert und immer gleich")
    void staedteSindSortiert() {
        // In UMGEKEHRTER Reihenfolge angelegt: Sonst waere eine Fassung gruen, die
        // einfach die Einfuegereihenfolge zurueckgibt.
        BesitzLager lager = lager();
        lager.freistadtAnlegen(STADTWELT, 2, 2, anna, 400_000L, "zwiesel");
        lager.freistadtAnlegen(STADTWELT, -1, 0, bert, 400_000L, "aue");
        lager.freistadtAnlegen(STADTWELT, 5, -3, anna, 400_000L, "moor");

        assertEquals(List.of("aue", "moor", "zwiesel"),
                lager.besitzIn(STADTWELT, Art.FREISTADT).stream().map(Parzelle::name).toList());
        assertEquals(lager.besitzIn(STADTWELT, Art.FREISTADT), lager.besitzIn(STADTWELT, Art.FREISTADT),
                "zweimal gefragt, zweimal dasselbe");
    }

    @Test
    @DisplayName("Die Staedteliste nennt nur Staedte und nur aus DIESER Welt")
    void staedtelisteIstEng() {
        BesitzLager lager = lager();
        lager.anlegen(WELT, 1, 1, anna, 0L);
        lager.freistadtAnlegen(STADTWELT, 1, 1, anna, 400_000L, "hier");
        lager.freistadtAnlegen("freimarken:andere", 1, 1, bert, 400_000L, "dort");

        assertEquals(List.of("hier"),
                lager.besitzIn(STADTWELT, Art.FREISTADT).stream().map(Parzelle::name).toList());
        assertTrue(lager.besitzIn(WELT, Art.FREISTADT).isEmpty(),
                "in der Marktstadt steht eine Parzelle, aber keine Stadt");
    }

    @Test
    @DisplayName("Parzellen eines Spielers werden gezaehlt und sortiert geliefert")
    void nachSpieler() {
        BesitzLager lager = lager();
        lager.anlegen(WELT, 5, 5, anna, 0L);
        lager.anlegen(WELT, 1, 9, anna, 25_000L);
        lager.anlegen(WELT, 3, 3, bert, 0L);

        assertEquals(2, lager.anzahlVon(WELT, anna));
        assertEquals(1, lager.anzahlVon(WELT, bert));

        List<Parzelle> annas = lager.vonSpieler(WELT, anna);
        assertEquals(2, annas.size());
        assertEquals(1, annas.get(0).x(), "nach X sortiert");
        assertEquals(5, annas.get(1).x());
    }

    @Test
    @DisplayName("Mitbauer werden gespeichert und wieder gelesen")
    void mitbauer() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 7, 7, anna, 0L);

        lager.mitbauerSetzen(parzelle.id(), Set.of(bert));
        Parzelle mit = lager.finde(WELT, 7, 7).orElseThrow();
        assertEquals(Set.of(bert), mit.mitbauer());
        assertTrue(mit.darfBauen(bert));

        lager.mitbauerSetzen(parzelle.id(), Set.of());
        Parzelle ohne = lager.finde(WELT, 7, 7).orElseThrow();
        assertTrue(ohne.mitbauer().isEmpty());
        assertFalse(ohne.darfBauen(bert));
    }

    @Test
    @DisplayName("Eigentuemer und Preis bleiben beim Mitbauer-Setzen unangetastet")
    void mitbauerAendertSonstNichts() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 8, 8, anna, 50_000L);
        lager.mitbauerSetzen(parzelle.id(), Set.of(bert));
        Parzelle danach = lager.finde(WELT, 8, 8).orElseThrow();

        assertEquals(parzelle.id(), danach.id());
        assertEquals(anna, danach.eigentuemer());
        assertEquals(50_000L, danach.preis());
        assertEquals(parzelle.erworbenAm(), danach.erworbenAm());
        // Diese drei sind der eigentliche Grund, warum es diese Pruefung gibt: Eine
        // Umsetzung, die hier feste Werte einsetzt statt die alten durchzutragen, loescht
        // bei jeder Mitbauer-Aenderung still den Gruppenbezug - und zwar nur im Betrieb,
        // wo es keiner sieht.
        assertEquals(parzelle.art(), danach.art());
        assertEquals(parzelle.elternId(), danach.elternId());
        assertEquals(parzelle.zustand(), danach.zustand());
    }

    @Test
    @DisplayName("Anlegen erzeugt eigenstaendigen, laufenden Besitz auf dem Raster")
    void anlegenErzeugtEigenstaendigeParzelle() {
        BesitzLager lager = lager();
        Parzelle neu = lager.anlegen(WELT, 3, 4, anna, 0L);

        assertEquals(Art.PARZELLE, neu.art());
        assertEquals(Zustand.AKTIV, neu.zustand());
        assertEquals(Parzelle.KEIN_ELTERNTEIL, neu.elternId());
        assertFalse(neu.hatElternteil());
        assertTrue(neu.darfBauen(anna));
    }

    @Test
    @DisplayName("Gezaehlt wird genau das, was auch aufgelistet wird")
    void zaehlenUndAuflistenStimmenUeberein() {
        // Die Zahl aus anzahlVon geht in die Preisstaffel und in die Rangobergrenze, die
        // Liste aus vonSpieler in die Anzeige. Laufen sie auseinander, zahlt der Spieler
        // fuer etwas anderes, als er sieht.
        BesitzLager lager = lager();
        lager.anlegen(WELT, 1, 1, anna, 0L);
        lager.anlegen(WELT, 2, 2, anna, 25_000L);
        lager.anlegen(WELT, 3, 3, bert, 0L);

        assertEquals(lager.vonSpieler(WELT, anna).size(), lager.anzahlVon(WELT, anna));
        assertEquals(lager.vonSpieler(WELT, bert).size(), lager.anzahlVon(WELT, bert));
        assertEquals(2, lager.anzahlVon(WELT, anna));
    }

    // --- Gruppen ----------------------------------------------------------------

    @Test
    @DisplayName("Eine neue Gruppe nimmt ihre Felder auf und bekommt eine eigene Nummer")
    void gruppeAnlegen() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 25_000L);

        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                20_000L, List.of(eins.id(), zwei.id()));

        assertNotEquals(Parzelle.KEIN_ELTERNTEIL, gruppe);
        assertEquals(2, lager.kinderVon(gruppe).size());
        assertEquals(gruppe, lager.finde(WELT, 1, 0).orElseThrow().elternId());
        assertEquals(gruppe, lager.finde(WELT, 2, 0).orElseThrow().elternId());
        assertTrue(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil());
    }

    @Test
    @DisplayName("Die Felder behalten beim Zusammenlegen Eigentuemer, Preis und Lage")
    void gruppeAendertDieFelderNichtSonst() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 25_000L);

        lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 20_000L,
                List.of(eins.id(), zwei.id()));

        // Jede Zeile traegt ihren Eigentuemer weiter selbst - nur so bleibt darfBauen
        // eine Abfrage ohne Umweg ueber die Gruppe.
        Parzelle danach = lager.finde(WELT, 2, 0).orElseThrow();
        assertEquals(anna, danach.eigentuemer());
        assertEquals(25_000L, danach.preis());
        assertEquals(zwei.erworbenAm(), danach.erworbenAm());
        assertEquals(2, danach.x());
    }

    @Test
    @DisplayName("Eine Gruppe laesst sich um ein weiteres Feld erweitern")
    void gruppeErweitern() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        Parzelle drei = lager.anlegen(WELT, 3, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                20_000L, List.of(eins.id(), zwei.id()));

        long dieselbe = lager.gruppeSetzen(gruppe, WELT, anna, Set.of(), 30_000L,
                List.of(drei.id()));

        assertEquals(gruppe, dieselbe, "Erweitern legt keine zweite Gruppe an");
        assertEquals(3, lager.kinderVon(gruppe).size());
    }

    @Test
    @DisplayName("Mitbauer einer Gruppe stehen auf jedem einzelnen Feld")
    void mitbauerStehenAufJedemFeld() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna,
                Set.of(bert), 20_000L, List.of(eins.id(), zwei.id()));

        // Das ist das Durchschreiben aus Plan 6.1 - ohne es muesste darfBauen bei jedem
        // Block erst die Gruppe nachschlagen.
        assertTrue(lager.finde(WELT, 1, 0).orElseThrow().darfBauen(bert));
        assertTrue(lager.finde(WELT, 2, 0).orElseThrow().darfBauen(bert));

        lager.mitbauerSetzen(gruppe, Set.of());
        assertFalse(lager.finde(WELT, 1, 0).orElseThrow().darfBauen(bert));
        assertFalse(lager.finde(WELT, 2, 0).orElseThrow().darfBauen(bert));
    }

    @Test
    @DisplayName("Auftrennen gibt die Felder zurueck, ohne sie zu loeschen")
    void auftrennen() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 25_000L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                20_000L, List.of(eins.id(), zwei.id()));

        lager.auftrennen(gruppe);

        assertTrue(lager.kinderVon(gruppe).isEmpty());
        assertTrue(lager.finde(WELT, 1, 0).isPresent(), "Das Feld bleibt bestehen");
        assertFalse(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil());
        assertEquals(25_000L, lager.finde(WELT, 2, 0).orElseThrow().preis());
    }

    @Test
    @DisplayName("Ein einzelnes Feld laesst sich loesen, die Gruppe bleibt")
    void ausGruppeLoesen() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        Parzelle drei = lager.anlegen(WELT, 3, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                30_000L, List.of(eins.id(), zwei.id(), drei.id()));

        lager.ausGruppeLoesen(drei.id());

        assertEquals(2, lager.kinderVon(gruppe).size(), "die Gruppe behaelt zwei Felder");
        assertFalse(lager.finde(WELT, 3, 0).orElseThrow().hatElternteil(),
                "das geloeste Feld ist eigenstaendig");
        assertTrue(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil(),
                "die anderen bleiben in der Gruppe");
        assertTrue(lager.finde(WELT, 3, 0).isPresent(), "das Feld bleibt bestehen");
    }

    @Test
    @DisplayName("Ein Feld ohne Gruppe laesst sich nicht loesen")
    void loesenOhneGruppeLaut() {
        // Lieber ein Abbruch als stilles Nichtstun: Die Welt wird nach dieser Zusage
        // umgebaut, und ein Weg, der zurueckkommt, obwohl die Gruppe noch besteht, waere
        // eine Mauer quer durch ein Grundstueck.
        BesitzLager lager = lager();
        Parzelle allein = lager.anlegen(WELT, 1, 0, anna, 0L);

        assertThrows(IllegalStateException.class, () -> lager.ausGruppeLoesen(allein.id()));
        assertThrows(IllegalStateException.class, () -> lager.ausGruppeLoesen(4711L));
    }

    @Test
    @DisplayName("Ein fremdes Feld laesst sich nicht an die eigene Gruppe haengen")
    void fremdesFeldGehtNicht() {
        BesitzLager lager = lager();
        Parzelle meins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle fremdes = lager.anlegen(WELT, 2, 0, bert, 0L);

        assertThrows(IllegalStateException.class, () -> lager.gruppeSetzen(
                Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 20_000L,
                List.of(meins.id(), fremdes.id())));
        // Und nichts davon ist passiert.
        assertFalse(lager.finde(WELT, 1, 0).orElseThrow().hatElternteil());
        assertFalse(lager.finde(WELT, 2, 0).orElseThrow().hatElternteil());
    }

    @Test
    @DisplayName("Ein Feld laesst sich nicht an zwei Gruppen haengen")
    void feldNurInEinerGruppe() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        Parzelle drei = lager.anlegen(WELT, 3, 0, anna, 0L);
        lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 20_000L,
                List.of(eins.id(), zwei.id()));

        assertThrows(IllegalStateException.class, () -> lager.gruppeSetzen(
                Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 20_000L,
                List.of(drei.id(), zwei.id())));
    }

    @Test
    @DisplayName("Ein Feld einer Gruppe laesst sich nicht einzeln entfernen")
    void gruppeLaesstSichNichtEinzelnEntfernen() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 1, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 2, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(),
                20_000L, List.of(eins.id(), zwei.id()));

        assertThrows(IllegalStateException.class, () -> lager.entfernen(gruppe));
        assertEquals(2, lager.kinderVon(gruppe).size());
    }

    @Test
    @DisplayName("Entfernen gibt die Stelle wirklich frei")
    void entfernen() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 9, 9, anna, 0L);
        lager.entfernen(parzelle.id());

        assertTrue(lager.finde(WELT, 9, 9).isEmpty());
        assertEquals(0, lager.anzahlVon(WELT, anna));
        // Und danach kann sie jemand anders nehmen.
        assertEquals(bert, lager.anlegen(WELT, 9, 9, bert, 0L).eigentuemer());
    }

    @Test
    @DisplayName("Der Eigentuemer wechselt, und die Mitbauer sind weg")
    void eigentuemerSetzen() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 11, 11, anna, 25_000L);
        lager.mitbauerSetzen(parzelle.id(), Set.of(bert));

        lager.eigentuemerSetzen(parzelle.id(), bert);

        Parzelle danach = lager.finde(WELT, 11, 11).orElseThrow();
        assertEquals(bert, danach.eigentuemer());
        // Das Leeren ist der halbe Vertrag: Blieben die Mitbauer des Vorbesitzers
        // stehen, erbte der neue Eigentuemer Fremde mit Baurecht - hier ist anna noch
        // nicht einmal mehr Mitbauer, und bert als frueherer Mitbauer auch nicht.
        assertTrue(danach.mitbauer().isEmpty());
        assertFalse(danach.darfBauen(anna));
        // Und die Zaehlung wandert mit, sonst traegt die Rangobergrenze ins Leere.
        assertEquals(0, lager.anzahlVon(WELT, anna));
        assertEquals(1, lager.anzahlVon(WELT, bert));
    }

    @Test
    @DisplayName("Beim Besitzwechsel bleibt sonst alles stehen")
    void eigentuemerSetzenAendertSonstNichts() {
        BesitzLager lager = lager();
        // EINE FREISTADT und keine Rasterparzelle [Fund 22.11.2026]: Hier stand
        // anlegen(...), und das setzt JEDES Durchtragefeld auf seinen Vorgabewert - art
        // PARZELLE, kein Elternteil, Zustand AKTIV, kein Name, keine Rechte, keine
        // Kasse, keine Regeln, nie besucht. Die Probe verglich also Vorgabewerte mit
        // sich selbst und blieb gruen, wenn mitNeuemBesitzer die Durchtragung verlor.
        // Eine Freistadt traegt Name und Kasse von Anfang an, und die drei Setzer
        // darunter bringen Zustand, Regeln und Buergerrechte auf Werte, die keine
        // Vorgabe sind.
        Parzelle stadt = lager.freistadtAnlegen(WELT, 2, 2, anna, 50_000L, "Altfurt");
        lager.zustandSetzen(stadt.id(), Zustand.GEKUENDIGT);
        lager.regelnSetzen(stadt.id(), "Hier gilt Hausrecht.");
        lager.buergerrechteSetzen(stadt.id(), Set.of(Tat.BAUEN, Tat.BENUTZEN));
        lager.besuchSetzen(stadt.id(), LocalDate.of(2026, 11, 20));
        Parzelle vorher = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();

        lager.eigentuemerSetzen(stadt.id(), bert);
        Parzelle danach = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();

        assertEquals(bert, danach.eigentuemer(), "der Wechsel selbst muss stattfinden");
        assertEquals(vorher.id(), danach.id());
        // Der PREIS bleibt der gezahlte: Er ist die Grundlage der Preisstaffel und der
        // Erstattung. Haengte er am Eigentuemer, waere ein Besitzwechsel ein Weg, die
        // Staffel zurueckzusetzen.
        assertEquals(50_000L, danach.preis());
        assertEquals(vorher.erworbenAm(), danach.erworbenAm());
        assertEquals(vorher.art(), danach.art());
        assertEquals(vorher.elternId(), danach.elternId());
        // Diese fuenf sind der eigentliche Grund, warum es diese Pruefung gibt: Eine
        // Fassung von mitNeuemBesitzer, die feste Werte einsetzt statt die alten
        // durchzutragen, laesst die Stadt namenlos und ohne Kasse zurueck - und eine
        // Freistadt ohne Namen WIRFT. Das ist genau der Feldverlust vom 11.11.2026.
        assertEquals("Altfurt", danach.name());
        assertEquals(vorher.kasse(), danach.kasse());
        assertEquals("Hier gilt Hausrecht.", danach.regeln());
        assertEquals(Set.of(Tat.BAUEN, Tat.BENUTZEN), danach.buergerrechte());
        assertEquals(Zustand.GEKUENDIGT, danach.zustand());
        assertEquals(LocalDate.of(2026, 11, 20), danach.besucht());
    }

    @Test
    @DisplayName("Eine Nummer, die es nicht gibt, laesst sich nicht umschreiben")
    void eigentuemerSetzenAufNichts() {
        BesitzLager lager = lager();

        // Die anderen vier Setzer dieses Vertrags werfen hier, und MariaLager hat es als
        // einzige Setzmethode nicht geprueft [Fund 22.11.2026]: Sie committete und kehrte
        // ohne Klage zurueck. Die Lage ist nicht theoretisch - der Zwischenspeicher haelt
        // eine von Hand geloeschte Zeile bis zu 30 Sekunden, und genau dann meldete der
        // Befehl Erfolg fuer nichts.
        assertThrows(IllegalStateException.class,
                () -> lager.eigentuemerSetzen(999_999L, bert));
    }

    @Test
    @DisplayName("Der Eigentuemer einer Gruppe wechselt auf jedem einzelnen Feld")
    void eigentuemerSetzenSchreibtDurch() {
        BesitzLager lager = lager();
        Parzelle eins = lager.anlegen(WELT, 3, 0, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 4, 0, anna, 0L);
        long gruppe = lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna,
                Set.of(bert), 20_000L, List.of(eins.id(), zwei.id()));

        lager.eigentuemerSetzen(gruppe, bert);

        // Das Durchschreiben aus Plan 6.1: Jede Zeile traegt ihren Eigentuemer
        // vollstaendig, damit darfBauen am Kind eine Abfrage ohne Umweg bleibt. Eine
        // Umsetzung, die nur die Gruppenzeile anfasst, laesst die Felder dem Alten -
        // und der baut weiter, waehrend die Liste den Neuen zeigt.
        Parzelle feldEins = lager.finde(WELT, 3, 0).orElseThrow();
        Parzelle feldZwei = lager.finde(WELT, 4, 0).orElseThrow();
        assertEquals(bert, feldEins.eigentuemer());
        assertEquals(bert, feldZwei.eigentuemer());
        assertFalse(feldEins.darfBauen(anna));
        assertTrue(lager.kinderVon(gruppe).stream()
                .allMatch(kind -> kind.mitbauer().isEmpty()));
    }

    @Test
    @DisplayName("Ohne neuen Eigentuemer gibt es keinen Besitzwechsel")
    void eigentuemerSetzenOhneEmpfaenger() {
        BesitzLager lager = lager();
        Parzelle parzelle = lager.anlegen(WELT, 13, 13, anna, 0L);

        // Eine Parzelle ohne Eigentuemer waere ueber keinen Befehl mehr erreichbar:
        // /parzelle info findet sie, aber niemand kann sie aufgeben oder uebernehmen.
        assertThrows(IllegalArgumentException.class,
                () -> lager.eigentuemerSetzen(parzelle.id(), null));
        assertEquals(anna, lager.finde(WELT, 13, 13).orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Eine unbekannte Nummer zu entfernen ist kein Fehler")
    void entfernenVonNichts() {
        lager().entfernen(999_999L);
    }
}
