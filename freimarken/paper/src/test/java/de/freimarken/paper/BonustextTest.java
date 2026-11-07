package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bonusstand;
import de.freimarken.kern.Bonuswerte;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die WAHL des richtigen Satzes - das Einzige, was in {@link Bonustext} selbst liegt.
 *
 * <p>Der Wortlaut steht in der Sprachdatei und wird hier nicht abgeschrieben: Das BILD
 * haelt die festgenagelte Tafel in {@code BildGleichTest}. Geprueft wird, WELCHER Satz
 * zutrifft und mit welchen Zahlen - denn genau das weiss nur das Programm.
 */
@DisplayName("Die Zeilen des Tagesbonus")
class BonustextTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 7);

    private Sprache sprache;

    @BeforeEach
    void vorher() {
        sprache = Sprachprobe.spracheAusDemJar();
    }

    private String zahlung(long betrag, int serie, int laengste) {
        return Sprachprobe.bild(Bonustext.zahlungZeile(sprache,
                new Tagesbonus.Zahlung(betrag, serie, laengste, serie == 1),
                Bonuswerte.VORGABE));
    }

    @Test
    @DisplayName("Der erste Serientag nennt, was MORGEN kaeme")
    void ersterTagNenntMorgen() {
        // Der Grund, morgen wiederzukommen - und der Grund, warum dieser Satz einen
        // eigenen Platzhalter hat.
        String zeile = zahlung(500L, 1, 1);

        assertTrue(zeile.contains("500"), zeile);
        assertTrue(zeile.contains("600"),
                "der Betrag des ZWEITEN Tages muss dastehen: " + zeile);
        assertFalse(zeile.contains("Tage in Folge"),
                "bei Tag 1 waere eine Zaehlung sinnlos: " + zeile);
    }

    @Test
    @DisplayName("Der erste Tag nennt NIE eine Bestmarke - auch wenn er eine ist")
    void ersterTagIstKeineBestmarke() {
        // Die Reihenfolge der Pruefungen: Ein ganz neuer Spieler hat mit Tag 1 auch seine
        // laengste Serie. Stuende die Bestmarke zuerst, laese er "so lange wie noch nie"
        // an seinem ersten Tag - und das ist keine Bestmarke, sondern sein Anfang.
        String zeile = zahlung(500L, 1, 1);

        assertFalse(zeile.contains("noch nie"), zeile);
    }

    @Test
    @DisplayName("Ein fortgesetzter Tag nennt die Zahl der Tage")
    void fortgesetzterTag() {
        // Serie 5, Bestmarke 12 - also KEINE Bestmarke.
        String zeile = zahlung(900L, 5, 12);

        assertTrue(zeile.contains("900"), zeile);
        assertTrue(zeile.contains("5"), zeile);
        assertFalse(zeile.contains("noch nie"),
                "fuenf von zwoelf ist keine Bestmarke: " + zeile);
    }

    @Test
    @DisplayName("Eine eingestellte Bestmarke bekommt ihren EIGENEN Satz")
    void bestmarkeHatIhrenSatz() {
        // Serie 5, Bestmarke 5 - der Augenblick, in dem die Serie etwas bedeutet.
        String zeile = zahlung(900L, 5, 5);

        assertTrue(zeile.contains("noch nie"), zeile);
        assertTrue(zeile.contains("5"), zeile);
    }

    @Test
    @DisplayName("Die drei Zahlungssaetze sind WIRKLICH verschieden")
    void dreiVerschiedeneSaetze() {
        // Dieselben Zahlen, drei Lagen - und drei verschiedene Zeilen. Waeren zwei davon
        // gleich, waere einer der Saetze eine Zeile, die niemand liest.
        String ersterTag = zahlung(500L, 1, 1);
        String fortgesetzt = zahlung(900L, 5, 12);
        String bestmarke = zahlung(900L, 5, 5);

        assertFalse(ersterTag.equals(fortgesetzt));
        assertFalse(fortgesetzt.equals(bestmarke));
        assertFalse(ersterTag.equals(bestmarke));
    }

    @Test
    @DisplayName("Heute schon bekommen: die Serie, die laengste und der Betrag von morgen")
    void standNachDerZahlung() {
        String zeile = Sprachprobe.bild(Bonustext.standZeile(sprache,
                new Bonusstand(ANNA, HEUTE, 5, 12), Bonuswerte.VORGABE, true, 5, 0L));

        assertTrue(zeile.contains("5"), zeile);
        assertTrue(zeile.contains("12"), zeile);
        // Tag 6 braechte 1.000 Mark - und das ist, was morgen auf dem Spiel steht.
        assertTrue(zeile.contains("1.000"), zeile);
    }

    @Test
    @DisplayName("Heute noch offen: der Betrag von HEUTE und sein Serientag")
    void standVorDerZahlung() {
        // Der Stand steht noch auf gestern, Serie 4. Heute waere Tag 5 - und der Satz
        // muss SEINEN Betrag nennen und nicht den von Tag 4.
        String zeile = Sprachprobe.bild(Bonustext.standZeile(sprache,
                new Bonusstand(ANNA, HEUTE.minusDays(1), 4, 12), Bonuswerte.VORGABE,
                false, 5, 0L));

        assertTrue(zeile.contains("5"), zeile);
        assertTrue(zeile.contains("900"), "Tag 5 bringt 900 Mark: " + zeile);
    }

    @Test
    @DisplayName("Nach einer gerissenen Serie nennt die offene Zeile Tag 1 und nicht Tag 5")
    void gerisseneSerieZeigtTagEins() {
        // Die Stelle, die beim ersten Entwurf falsch war: Gerechnet wurde serie + 1, und
        // das ist nach einer gerissenen Serie zu viel. Darum kommt die Serie von
        // Serienregel herein und wird hier nicht gerechnet.
        String zeile = Sprachprobe.bild(Bonustext.standZeile(sprache,
                new Bonusstand(ANNA, HEUTE.minusDays(9), 12, 12), Bonuswerte.VORGABE,
                false, 1, 0L));

        assertTrue(zeile.contains("500"),
                "nach dem Riss bringt heute den Grundbetrag: " + zeile);
        assertFalse(zeile.contains("1.700"), zeile);
    }

    @Test
    @DisplayName("Die wartende Zeile nennt die fehlenden Minuten")
    void standWartet() {
        String zeile = Sprachprobe.bild(Bonustext.standZeile(sprache,
                new Bonusstand(ANNA, HEUTE.minusDays(1), 1, 1),
                Bonuswerte.VORGABE.mitMindestMinuten(10L), false, 2, 8L));

        assertTrue(zeile.contains("8"), zeile);
        assertTrue(zeile.contains("600"), "Tag 2 bringt 600 Mark: " + zeile);
        assertTrue(zeile.contains("taetige"),
                "gezaehlt werden TAETIGE Minuten und nicht Anwesenheit: " + zeile);
    }

    @Test
    @DisplayName("Ohne eingestellte Mindestzeit kommt die wartende Zeile NIE")
    void ohneMindestzeitKeinWarten() {
        // Auch wenn jemand fehlende Minuten hereingibt: Ohne eingestellte Zeit gibt es
        // nichts zu warten, und eine Zeile ueber Minuten waere eine Erfindung.
        String zeile = Sprachprobe.bild(Bonustext.standZeile(sprache,
                Bonusstand.leer(ANNA), Bonuswerte.VORGABE, false, 1, 99L));

        assertFalse(zeile.contains("taetige"), zeile);
        assertTrue(zeile.contains("500"), zeile);
    }

    @Test
    @DisplayName("Sind die Minuten erreicht, wartet die Zeile nicht mehr")
    void keineFehlendenMinutenMehr() {
        String zeile = Sprachprobe.bild(Bonustext.standZeile(sprache,
                Bonusstand.leer(ANNA), Bonuswerte.VORGABE.mitMindestMinuten(10L),
                false, 1, 0L));

        assertFalse(zeile.contains("taetige"),
                "bei 0 fehlenden Minuten steht der Bonus an und wartet nicht: " + zeile);
    }

    @Test
    @DisplayName("Jede der drei Standzeilen ist eine EIGENE")
    void dreiVerschiedeneStandzeilen() {
        String gezahlt = Sprachprobe.bild(Bonustext.standZeile(sprache,
                new Bonusstand(ANNA, HEUTE, 5, 12), Bonuswerte.VORGABE, true, 5, 0L));
        String offen = Sprachprobe.bild(Bonustext.standZeile(sprache,
                Bonusstand.leer(ANNA), Bonuswerte.VORGABE, false, 1, 0L));
        String wartet = Sprachprobe.bild(Bonustext.standZeile(sprache,
                Bonusstand.leer(ANNA), Bonuswerte.VORGABE.mitMindestMinuten(10L),
                false, 1, 10L));

        assertFalse(gezahlt.equals(offen));
        assertFalse(offen.equals(wartet));
        assertFalse(gezahlt.equals(wartet));
    }

    @Test
    @DisplayName("Jede Zeile ist GENAU EINE Zeile")
    void jedeZeileIstEine() {
        // Die Nachricht ueber eine Zahlung steht neben dem Beitritt und soll den Chat
        // nicht fluten; die Standzeile steht zwischen zwei anderen Zeilen von /mark. Eine
        // Umbruchmarke in einem dieser Saetze verschoebe das Bild stumm - sprache.eineZeile
        // wirft darum mit dem Pfad.
        assertEquals(1, zahlung(500L, 1, 1).split(System.lineSeparator()).length);
        assertEquals(1, Sprachprobe.bild(Bonustext.standZeile(sprache,
                        new Bonusstand(ANNA, HEUTE, 5, 12), Bonuswerte.VORGABE, true, 5,
                        0L))
                .split(System.lineSeparator()).length);
    }
}
