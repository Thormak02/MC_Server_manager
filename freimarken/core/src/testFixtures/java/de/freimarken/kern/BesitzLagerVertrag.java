package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(lager.staedteIn(STADTWELT).isEmpty());

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
        assertEquals(1, lager.staedteIn(STADTWELT).size());
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
        assertEquals(1, lager.staedteIn(STADTWELT).size());
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
                lager.staedteIn(STADTWELT).stream().map(Parzelle::name).toList());
        assertEquals(lager.staedteIn(STADTWELT), lager.staedteIn(STADTWELT),
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
                lager.staedteIn(STADTWELT).stream().map(Parzelle::name).toList());
        assertTrue(lager.staedteIn(WELT).isEmpty(),
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
    @DisplayName("Eine unbekannte Nummer zu entfernen ist kein Fehler")
    void entfernenVonNichts() {
        lager().entfernen(999_999L);
    }
}
