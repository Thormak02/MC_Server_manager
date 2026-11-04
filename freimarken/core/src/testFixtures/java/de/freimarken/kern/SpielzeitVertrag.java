package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede Spielzeitablage einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link KasseVertrag} und {@link BesitzLagerVertrag}: Die Umsetzung
 * bringt ihre eigene Testklasse mit und erbt diese Pruefungen. Dadurch wird eine Umsetzung,
 * die sich anders verhaelt als die andere, in einem Testlauf sichtbar und nicht im Betrieb.
 *
 * <p>Die wichtigste Probe hier ist {@link #additivUndNichtSetzend()}. Spielzeit hat keinen
 * Zeugen: Anders als beim Geld, wo die Summe aller Buchungen den Stand ergeben muss,
 * widerspricht einer verlorenen oder doppelten Minute nichts. Was sich nicht nachrechnen
 * laesst, muss zumindest an einer festen Zusage haengen - und die ist: aufschlagen, nie
 * setzen.
 *
 * <p>Hier stehen ALLE sechs Zusagen aus dem Klassenkopf von {@link Spielzeitablage}, auch die
 * vierte ("eine Liste in einem Aufruf, alles oder nichts"). Die hatte vorher jede Umsetzung
 * als eigene Probe neben sich - mit demselben Aufbau und derselben Absicht -, und eine dritte
 * Umsetzung haette alle anderen Proben geerbt, nur diese nicht: Sie haette die halbe Liste
 * schreiben koennen, ohne dass etwas rot wird. Genau das zu verhindern ist der Zweck eines
 * geteilten Vertrags, und eine Zusage im Klassenkopf, die er nicht prueft, ist die schlechteste
 * Stelle, an der eine Luecke sitzen kann.
 */
public abstract class SpielzeitVertrag {

    /** Ein Tag, der in jeder Zone derselbe ist, weil er nur als Schluessel dient. */
    protected static final LocalDate HEUTE = LocalDate.of(2026, 11, 3);

    /** Der Vortag - fuer die Probe, dass ein neuer Tag eine neue Zeile ist. */
    protected static final LocalDate GESTERN = HEUTE.minusDays(1);

    /** Eine frische, leere Ablage. */
    protected abstract Spielzeitablage ablage();

    @Test
    @DisplayName("Ein unbekannter Spieler hat nichts - jeder faengt bei 0 an")
    void unbekannterHatNichts() {
        Spielzeitstand stand = ablage().lies(UUID.randomUUID(), HEUTE);

        assertEquals(0L, stand.sekundenGesamt());
        assertEquals(0L, stand.sekundenHeute());
        assertEquals(HEUTE, stand.tag(), "der Tag muss der sein, der hereingegeben wurde");
    }

    @Test
    @DisplayName("Zwei Zuschlaege am selben Tag addieren sich - sie setzen nicht")
    void additivUndNichtSetzend() {
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 60L)));
        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 45L)));

        assertEquals(105L, ablage.lies(anna, HEUTE).sekundenHeute(),
                "additiv und nicht lesen-rechnen-schreiben: sonst verschluckt ein zweiter"
                        + " Backend-Server den Zuwachs des ersten");
    }

    @Test
    @DisplayName("Ein Zuschlag mit neuem Tag legt eine Zeile an und laesst die alte stehen")
    void neuerTagNeueZeile() {
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, GESTERN, 3_600L)));

        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 120L)));

        Spielzeitstand stand = ablage.lies(anna, HEUTE);
        assertEquals(3_720L, stand.sekundenGesamt(), "gesamt ist die Summe aller Tage");
        assertEquals(120L, stand.sekundenHeute(), "heute ist nur der genannte Tag");
    }

    @Test
    @DisplayName("Mehrere Zuschlaege in EINEM Aufruf werden alle uebernommen")
    void einAufrufVieleZuschlaege() {
        // Der Takt schreibt alle anwesenden Spieler in einem Zug, damit die Datenschicht sie
        // in eine Transaktion legen kann.
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.schreibeFort(List.of(
                new Spielzeitablage.Zuschlag(anna, GESTERN, 30L),
                new Spielzeitablage.Zuschlag(anna, HEUTE, 30L),
                new Spielzeitablage.Zuschlag(bert, HEUTE, 90L)));

        assertEquals(60L, ablage.lies(anna, HEUTE).sekundenGesamt());
        assertEquals(90L, ablage.lies(bert, HEUTE).sekundenGesamt());
    }

    @Test
    @DisplayName("Zwei Zuschlaege fuer denselben Spieler und Tag in einem Aufruf addieren sich")
    void zweimalImSelbenAufruf() {
        // Der Fall entsteht bei einem Takt ueber Mitternacht: Die geteilte Spanne kann
        // zweimal denselben Tag nennen, wenn der Nachtrag eines abgemeldeten Spielers
        // mitlaeuft.
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibeFort(List.of(
                new Spielzeitablage.Zuschlag(anna, HEUTE, 20L),
                new Spielzeitablage.Zuschlag(anna, HEUTE, 22L)));

        assertEquals(42L, ablage.lies(anna, HEUTE).sekundenHeute());
    }

    @Test
    @DisplayName("Jeder Spieler hat seine eigene Zeit")
    void spielerSindGetrennt() {
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 600L)));

        assertEquals(600L, ablage.lies(anna, HEUTE).sekundenGesamt());
        assertEquals(0L, ablage.lies(bert, HEUTE).sekundenGesamt());
    }

    @Test
    @DisplayName("Ein Tag ohne Zeile ist heute 0 und aendert die Gesamtzeit nicht")
    void tagOhneZeile() {
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, GESTERN, 500L)));

        Spielzeitstand stand = ablage.lies(anna, HEUTE);

        assertEquals(500L, stand.sekundenGesamt());
        assertEquals(0L, stand.sekundenHeute());
    }

    @Test
    @DisplayName("Eine leere Liste ist erlaubt und aendert nichts")
    void leereListeGehtDurch() {
        // Ein Takt, in dem niemand online ist oder jeder untaetig war, hat nichts zu
        // schreiben - und darf deshalb nicht klagen.
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 10L)));

        ablage.schreibeFort(List.of());

        assertEquals(10L, ablage.lies(anna, HEUTE).sekundenGesamt());
    }

    @Test
    @DisplayName("Ein Zuschlag von 0 Sekunden aendert nichts")
    void nullSekundenAendernNichts() {
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 0L)));

        assertEquals(0L, ablage.lies(anna, HEUTE).sekundenGesamt());
    }

    @Test
    @DisplayName("Ein negativer Zuschlag faellt beim Bauen auf, nicht erst beim Schreiben")
    void negativFliegtAuf() {
        // An der Quelle und nicht in der Datenbank: Sonst behandelte jede Umsetzung einen
        // Rechenfehler anders, und es gibt kein Abziehen von Spielzeit.
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitablage.Zuschlag(anna, HEUTE, -1L));
    }

    @Test
    @DisplayName("Ohne Liste, ohne Spieler und ohne Tag geht nichts")
    void pflichtangaben() {
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ablage.schreibeFort(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.lies(null, HEUTE));
        assertThrows(IllegalArgumentException.class, () -> ablage.lies(anna, null));
    }

    @Test
    @DisplayName("Ein null-Eintrag mitten in der Liste hinterlaesst nichts - alles oder nichts")
    void allesOderNichts() {
        // Die vierte der sechs Zusagen, und zwar umsetzungsfrei: Die Datenbankfassung legt
        // die Liste in EINE Transaktion, die Speicherfassung rechnet erst vollstaendig in
        // eine Nebenkarte und uebernimmt dann. Beide muessen dasselbe tun - ein Takt, der
        // die Haelfte seiner Spieler geschrieben hat und dann abbricht, waere nicht
        // nachvollziehbar, und der Verlust liesse sich nicht ausrechnen, weil die
        // Protokollzeile die ganze Liste nennt.
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        List<Spielzeitablage.Zuschlag> mitLuecke = new ArrayList<>();
        mitLuecke.add(new Spielzeitablage.Zuschlag(anna, HEUTE, 60L));
        mitLuecke.add(null);

        assertThrows(IllegalArgumentException.class, () -> ablage.schreibeFort(mitLuecke));

        assertEquals(0L, ablage.lies(anna, HEUTE).sekundenGesamt(),
                "nach einem abgewiesenen Aufruf darf keine Haelfte stehen");
    }

    @Test
    @DisplayName("Ein Ueberlauf bricht ab, statt lautlos eine negative Zeit zu machen")
    void ueberlaufBrichtAb() {
        // Aus dem Takt heraus unerreichbar - Spielzeitregel klemmt jede Spanne auf zehn
        // Taktlaengen. Erreichbar ist die Stelle nur fuer einen kuenftigen Aufrufer der
        // Schnittstelle, also fuer genau den, dem dieser Vertrag etwas verspricht. Darum
        // steht die Probe hier und nicht bei einer der beiden Fassungen: Sie legt den
        // AUSNAHMETYP fest. IllegalStateException und nicht ArithmeticException, weil
        // MariaDB den Ueberlauf als SQLException meldet (Fehler 1690) und die Hausform
        // dafuer IllegalStateException ist; die umgekehrte Richtung braeuchte eine Abfrage
        // von Herstellerfehlernummern in MariaSpielzeit.
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibeFort(List.of(
                new Spielzeitablage.Zuschlag(anna, HEUTE, Long.MAX_VALUE)));

        assertThrows(IllegalStateException.class, () -> ablage.schreibeFort(
                List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 1L))));

        assertEquals(Long.MAX_VALUE, ablage.lies(anna, HEUTE).sekundenHeute(),
                "und der vorhandene Wert bleibt stehen, als waere nichts geschrieben");
    }

    @Test
    @DisplayName("Auch beim LESEN bricht ein Ueberlauf ab, statt eine falsche Summe zu nennen")
    void ueberlaufBeimLesenBrichtAb() {
        // Erreichbar auf demselben Weg wie ueberlaufBrichtAb, und vom Schreibweg NICHT
        // gedeckt: Der haelt jede einzelne Tageszeile unter Long.MAX_VALUE, die SUMME
        // ueber mehrere Tage aber nicht. Zwei Tage mit je Long.MAX_VALUE nimmt jede
        // Fassung widerspruchslos an - erst das Lesen kann es merken.
        //
        // Auch hier legt die Probe den AUSNAHMETYP fest, und zwar fuer beide Fassungen:
        // MariaDB summiert BIGINT als DECIMAL, bildet die Summe also noch und kann sie
        // nur nicht mehr als long herausgeben (SQLException, von MariaSpielzeit in die
        // Hausform gebracht). Die Speicherfassung rechnet mit Math.addExact. Ohne diese
        // Zusage antwortet die eine Fassung anders als die andere, und der Aufrufer
        // faengt die falsche Ausnahme - genau die Luecke, gegen die ein geteilter
        // Vertrag gebaut ist. Eine stumme falsche Summe waere das Schlimmste: Spielzeit
        // hat keinen Zeugen, der ihr widerspricht.
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibeFort(List.of(
                new Spielzeitablage.Zuschlag(anna, GESTERN, Long.MAX_VALUE),
                new Spielzeitablage.Zuschlag(anna, HEUTE, Long.MAX_VALUE)));

        assertThrows(IllegalStateException.class, () -> ablage.lies(anna, HEUTE));
    }

    @Test
    @DisplayName("Viele Takte hintereinander ergeben die Summe")
    void vieleTakte() {
        Spielzeitablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        for (int i = 0; i < 40; i++) {
            ablage.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 60L)));
        }

        assertEquals(2_400L, ablage.lies(anna, HEUTE).sekundenHeute());
    }
}
