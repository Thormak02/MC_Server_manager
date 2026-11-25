package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede {@link Meldeablage} einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link TeamVertrag} und {@link RangVertrag}: Die Umsetzung bringt
 * ihre eigene Testklasse mit und erbt diese Pruefungen.
 *
 * <p>Hier stehen alle sechs Versprechen aus dem Klassenkopf von {@link Meldeablage}. Das
 * wichtigste ist das DRITTE, und es ist das einzige, das man im Betrieb nie bemerkt:
 * <b>{@link Meldeablage#schreibe} schreibt nur bei erwartetem Stand.</b> Ohne diesen
 * Riegel nehmen zwei Moderatoren dieselbe Meldung, beide lesen "genommen", und beide
 * sprechen denselben Spieler an. Im Protokoll sieht das wie zwei getrennte Vorgaenge aus.
 */
public abstract class MeldeVertrag {

    /** Ein fester Zeitpunkt - eine Ablage hat keine eigene Uhr. */
    protected static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    /** Eine Minute spaeter. */
    protected static final Instant SPAETER = MITTAGS.plusSeconds(60L);

    /** Zwei Minuten spaeter - fuer die Reihenfolge nach Alter. */
    protected static final Instant NOCH_SPAETER = MITTAGS.plusSeconds(120L);

    protected static final String WELT = "altfurt";

    /** Eine frische, leere Ablage. */
    protected abstract Meldeablage ablage();

    @Test
    @DisplayName("Eine gelegte Meldung kommt MIT Nummer zurueck und ist OFFEN")
    void gelegteMeldungKommtMitNummer() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        Meldung eine = ablage.lege(anna, bert, "baut in meiner Parzelle", WELT,
                10, -60, -20, MITTAGS);

        // DAS ERSTE UND ZWEITE VERSPRECHEN. Ohne die zurueckgegebene Nummer muesste der
        // Aufrufer suchen - und bei zwei gleichzeitigen Meldungen desselben Melders
        // griffe er zur falschen.
        assertTrue(eine.nummer() > 0L, "die Ablage vergibt die Nummer: " + eine.nummer());
        assertEquals(Meldestand.OFFEN, eine.stand());
        assertEquals(anna, eine.melder());
        assertEquals(bert, eine.betroffener());
        assertEquals("baut in meiner Parzelle", eine.grund());
        assertEquals(WELT, eine.welt());
        assertEquals(10, eine.x());
        assertEquals(-60, eine.y());
        assertEquals(-20, eine.z());
        assertEquals(MITTAGS, eine.gemeldetAm(),
                "die Zeit kommt von aussen und nicht aus der Ablage");
        assertTrue(eine.wirdBearbeitetVon().isEmpty());
        assertFalse(eine.gelesen());
    }

    @Test
    @DisplayName("Zwei Meldungen bekommen VERSCHIEDENE Nummern")
    void zweiMeldungenZweiNummern() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        long erste = ablage.lege(anna, bert, "erster Vorfall", WELT, 1, -60, 1,
                MITTAGS).nummer();
        long zweite = ablage.lege(anna, bert, "zweiter Vorfall", WELT, 2, -60, 2,
                SPAETER).nummer();

        // Zwei gleiche Nummern waeren zwei Meldungen, von denen eine jede Bearbeitung
        // der anderen ueberschreibt.
        assertTrue(zweite > erste, erste + " dann " + zweite);
    }

    @Test
    @DisplayName("Eine gelegte Meldung ist unter ihrer Nummer wieder zu lesen")
    void gelegteMeldungIstLesbar() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        Meldung gelegt = ablage.lege(anna, bert, "Beleidigung im Chat", WELT,
                5, -60, 7, MITTAGS);

        Meldung gelesen = ablage.lies(gelegt.nummer()).orElseThrow();

        assertEquals(gelegt, gelesen, "dasselbe hinein wie heraus");
    }

    @Test
    @DisplayName("Eine unbekannte Nummer gibt leer - und keinen Fehler")
    void unbekannteNummerGibtLeer() {
        // DAS VIERTE VERSPRECHEN: Ein Moderator vertippt sich in der Nummer, und das ist
        // eine Antwort, kein Absturz.
        assertTrue(ablage().lies(4711L).isEmpty());
    }

    @Test
    @DisplayName("DER RIEGEL: Beim zweiten Nehmen schreibt nur der ERSTE")
    void zweiterNehmerVerliert() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        UUID zweiter = UUID.randomUUID();
        Meldung offen = ablage.lege(anna, bert, "Griefing an der Mauer", WELT,
                0, -60, 0, MITTAGS);

        // Beide lesen denselben OFFENEN Stand - das ist der Augenblick, in dem der
        // Riegel greifen muss.
        assertTrue(ablage.schreibe(offen.inArbeitVon(moderator, SPAETER),
                Meldestand.OFFEN), "der erste gewinnt");

        // DAS DRITTE VERSPRECHEN. Ohne ihn sprechen beide denselben Spieler an, und im
        // Protokoll sieht das wie zwei getrennte Vorgaenge aus.
        assertFalse(ablage.schreibe(offen.inArbeitVon(zweiter, NOCH_SPAETER),
                Meldestand.OFFEN), "der zweite verliert");

        Meldung jetzt = ablage.lies(offen.nummer()).orElseThrow();
        assertEquals(moderator, jetzt.wirdBearbeitetVon().orElseThrow(),
                "und es ist NICHTS vom zweiten uebrig");
        assertEquals(Meldestand.IN_ARBEIT, jetzt.stand());
    }

    @Test
    @DisplayName("Schreiben mit passendem Stand geht durch")
    void schreibenMitPassendemStand() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        Meldung offen = ablage.lege(anna, UUID.randomUUID(), "Vorfall", WELT,
                0, -60, 0, MITTAGS);
        Meldung inArbeit = offen.inArbeitVon(moderator, SPAETER);
        assertTrue(ablage.schreibe(inArbeit, Meldestand.OFFEN), "die Vorbereitung");

        // Die Gegenprobe zum Riegel: Mit dem RICHTIGEN erwarteten Stand geht es. Ohne
        // diese Probe koennte schreibe() immer false geben, und der Riegel waere gruen.
        assertTrue(ablage.schreibe(
                        inArbeit.erledigt(Meldestand.ABGESCHLOSSEN, moderator,
                                "angesprochen, hat aufgehoert", NOCH_SPAETER),
                        Meldestand.IN_ARBEIT));
        Meldung fertig = ablage.lies(offen.nummer()).orElseThrow();
        assertEquals(Meldestand.ABGESCHLOSSEN, fertig.stand());
        assertEquals("angesprochen, hat aufgehoert", fertig.vermerk());
        assertEquals(NOCH_SPAETER, fertig.erledigt().orElseThrow());
    }

    @Test
    @DisplayName("Eine Nummer, die es nicht gibt, wirft beim Schreiben")
    void schreibenOhneNummerWirft() {
        Meldeablage ablage = ablage();
        Meldung erfunden = Meldung.neu(999L, UUID.randomUUID(), UUID.randomUUID(),
                "nie gelegt", WELT, 0, -60, 0, MITTAGS);

        // Ein Wurf und kein false: false hiesse "versuch es noch einmal", und das
        // koennte nie gelingen. Das ist ein Rechenfehler im Aufrufer, und er soll laut
        // sein.
        assertThrows(IllegalArgumentException.class,
                () -> ablage.schreibe(erfunden, Meldestand.OFFEN));
    }

    @Test
    @DisplayName("Die offenen Meldungen kommen mit der AELTESTEN zuerst")
    void offeneAeltesteZuerst() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        // ABSICHTLICH in der falschen Reihenfolge gelegt: Die juengste zuerst. Ohne die
        // zugesagte Sortierung gaebe eine Karte die Einfuegereihenfolge zurueck, und
        // diese Probe waere gruen, ohne etwas zu pruefen.
        long juengste = ablage.lege(anna, bert, "zuletzt passiert", WELT, 0, -60, 0,
                NOCH_SPAETER).nummer();
        long aelteste = ablage.lege(anna, bert, "zuerst passiert", WELT, 0, -60, 0,
                MITTAGS).nummer();

        List<Meldung> offene = ablage.offene(10);

        // DAS FUENFTE VERSPRECHEN. Eine Liste, die mit der neuesten beginnt, laesst die
        // aelteste fuer immer unten stehen - und genau die wartet am laengsten.
        assertEquals(List.of(aelteste, juengste),
                offene.stream().map(Meldung::nummer).toList());
    }

    @Test
    @DisplayName("Erledigte Meldungen stehen NICHT mehr in der offenen Liste")
    void erledigteNichtInDerListe() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        Meldung eine = ablage.lege(anna, UUID.randomUUID(), "erledigt sich", WELT,
                0, -60, 0, MITTAGS);
        Meldung bleibt = ablage.lege(anna, UUID.randomUUID(), "bleibt offen", WELT,
                0, -60, 0, SPAETER);
        assertTrue(ablage.schreibe(eine.erledigt(Meldestand.ABGEWIESEN, moderator,
                "kein Verstoss", SPAETER), Meldestand.OFFEN), "die Vorbereitung");

        assertEquals(List.of(bleibt.nummer()),
                ablage.offene(10).stream().map(Meldung::nummer).toList());
    }

    @Test
    @DisplayName("Eine Meldung IN ARBEIT steht weiter in der Liste")
    void inArbeitBleibtInDerListe() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        Meldung eine = ablage.lege(anna, UUID.randomUUID(), "wird bearbeitet", WELT,
                0, -60, 0, MITTAGS);
        assertTrue(ablage.schreibe(eine.inArbeitVon(moderator, SPAETER),
                Meldestand.OFFEN), "die Vorbereitung");

        // Sonst verschwaende eine Meldung, sobald sie jemand nimmt - und ein Moderator,
        // der aufhoert, haette sie unsichtbar mitgenommen. Darum nennt die Liste auch
        // die in Arbeit befindlichen.
        assertEquals(1, ablage.offene(10).size(), ablage.offene(10).toString());
        assertEquals(Meldestand.IN_ARBEIT, ablage.offene(10).get(0).stand());
    }

    @Test
    @DisplayName("Die Liste haelt ihre Hoechstzahl ein")
    void listeHaeltIhreGrenze() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        for (int nummer = 0; nummer < 5; nummer++) {
            ablage.lege(anna, UUID.randomUUID(), "Vorfall " + nummer, WELT, 0, -60, 0,
                    MITTAGS.plusSeconds(nummer));
        }

        assertEquals(2, ablage.offene(2).size());
        // Und es sind die ZWEI AELTESTEN, nicht zwei beliebige.
        assertEquals("Vorfall 0", ablage.offene(2).get(0).grund());
        assertEquals("Vorfall 1", ablage.offene(2).get(1).grund());
    }

    @Test
    @DisplayName("offeneVon zaehlt nur die UNERLEDIGTEN dieses Melders")
    void offeneVonZaehltRichtig() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        Meldung ihre = ablage.lege(anna, UUID.randomUUID(), "annas Vorfall", WELT,
                0, -60, 0, MITTAGS);
        ablage.lege(anna, UUID.randomUUID(), "annas zweiter", WELT, 0, -60, 0, SPAETER);
        ablage.lege(bert, UUID.randomUUID(), "berts Vorfall", WELT, 0, -60, 0, SPAETER);

        assertEquals(2, ablage.offeneVon(anna));
        assertEquals(1, ablage.offeneVon(bert));

        assertTrue(ablage.schreibe(ihre.erledigt(Meldestand.ABGESCHLOSSEN, moderator,
                "erledigt", NOCH_SPAETER), Meldestand.OFFEN), "die Vorbereitung");

        // Die Hoechstzahl je Melder zaehlt die UNERLEDIGTEN - sonst waere das Meldewesen
        // fuer einen fleissigen Melder nach drei Meldungen fuer immer zu.
        assertEquals(1, ablage.offeneVon(anna));
    }

    @Test
    @DisplayName("Wer nie gemeldet hat, hat keine letzte Meldung")
    void nieGemeldetKeineLetzte() {
        assertTrue(ablage().letzteVon(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("letzteVon gibt die JUENGSTE, nicht die zuletzt gelegte")
    void letzteVonGibtDieJuengste() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        // Die JUENGERE zuerst gelegt: Einfuegereihenfolge und Zeitreihenfolge fallen
        // auseinander. Ohne diese Trennung waere die Probe gruen, egal welche die
        // Ablage zurueckgibt - und die Abklingzeit rechnete mit der falschen.
        ablage.lege(anna, UUID.randomUUID(), "spaeterer Vorfall", WELT, 0, -60, 0,
                NOCH_SPAETER);
        ablage.lege(anna, UUID.randomUUID(), "fruehere Tat", WELT, 0, -60, 0, MITTAGS);

        assertEquals(NOCH_SPAETER, ablage.letzteVon(anna).orElseThrow());
    }

    @Test
    @DisplayName("Ungelesene Antworten: nur beantwortete, nur eigene, nur ungelesene")
    void ungeleseneAntworten() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        Meldung offen = ablage.lege(anna, UUID.randomUUID(), "noch offen", WELT,
                0, -60, 0, MITTAGS);
        Meldung beantwortet = ablage.lege(anna, UUID.randomUUID(), "beantwortet", WELT,
                0, -60, 0, SPAETER);
        Meldung fremde = ablage.lege(bert, UUID.randomUUID(), "berts Sache", WELT,
                0, -60, 0, SPAETER);
        assertTrue(ablage.schreibe(beantwortet.erledigt(Meldestand.ABGESCHLOSSEN,
                moderator, "gemacht", NOCH_SPAETER), Meldestand.OFFEN), "Vorbereitung");
        assertTrue(ablage.schreibe(fremde.erledigt(Meldestand.ABGEWIESEN, moderator,
                "nichts dran", NOCH_SPAETER), Meldestand.OFFEN), "Vorbereitung");

        List<Meldung> warten = ablage.ungeleseneVon(anna);

        // DAS SECHSTE VERSPRECHEN in seiner Wirkung: Ohne diese Liste verschwindet die
        // Rueckmeldung, sobald der Melder beim Abschliessen offline ist.
        assertEquals(List.of(beantwortet.nummer()),
                warten.stream().map(Meldung::nummer).toList());
        assertFalse(warten.stream().anyMatch(m -> m.nummer() == offen.nummer()),
                "eine offene Meldung ist keine Antwort");
        assertFalse(warten.stream().anyMatch(m -> m.nummer() == fremde.nummer()),
                "und berts Antwort gehoert bert");
    }

    @Test
    @DisplayName("Eine als gelesen vermerkte Antwort wartet nicht mehr")
    void gelesenWartetNichtMehr() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        Meldung eine = ablage.lege(anna, UUID.randomUUID(), "Vorfall", WELT,
                0, -60, 0, MITTAGS);
        Meldung fertig = eine.erledigt(Meldestand.ABGESCHLOSSEN, moderator, "erledigt",
                SPAETER);
        assertTrue(ablage.schreibe(fertig, Meldestand.OFFEN), "die Vorbereitung");
        assertEquals(1, ablage.ungeleseneVon(anna).size());

        assertTrue(ablage.schreibe(fertig.alsGelesen(), Meldestand.ABGESCHLOSSEN));

        assertEquals(List.of(), ablage.ungeleseneVon(anna),
                "sonst liest der Melder dieselbe Antwort bei jedem Beitritt erneut");
    }

    @Test
    @DisplayName("Die Ablage prueft NICHT, ob gemeldet werden darf")
    void keinUrteilInDerAblage() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        // DAS SECHSTE VERSPRECHEN: Hundert Meldungen in derselben Sekunde - keine
        // Abklingzeit, keine Hoechstzahl. Das prueft Meldebuch mit den Werten aus der
        // Datei. Eine Ablage, die mitpruefte, waere ein zweiter Riegel neben dem ersten.
        for (int nummer = 0; nummer < 20; nummer++) {
            ablage.lege(anna, UUID.randomUUID(), "Vorfall " + nummer, WELT, 0, -60, 0,
                    MITTAGS);
        }

        assertEquals(20, ablage.offeneVon(anna));
    }

    @Test
    @DisplayName("Zwei Melder stehen sich nicht im Weg")
    void zweiMelderGetrennt() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        ablage.lege(anna, UUID.randomUUID(), "annas Sache", WELT, 0, -60, 0, MITTAGS);

        assertEquals(1, ablage.offeneVon(anna));
        assertEquals(0, ablage.offeneVon(bert));
        assertTrue(ablage.letzteVon(bert).isEmpty());
        assertEquals(List.of(), ablage.ungeleseneVon(bert));
    }

    @Test
    @DisplayName("Fehlende Angaben klagen")
    void fehlendeAngabenKlagen() {
        Meldeablage ablage = ablage();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> ablage.lege(null, anna, "Grund", WELT, 0, -60, 0, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.lege(anna, null, "Grund", WELT, 0, -60, 0, MITTAGS));
        // Niemand meldet sich selbst - das prueft Meldung schon im Konstruktor, und
        // darum kann die Ablage den Zustand gar nicht halten.
        assertThrows(IllegalArgumentException.class,
                () -> ablage.lege(anna, anna, "Grund", WELT, 0, -60, 0, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.lege(anna, UUID.randomUUID(), "x", WELT, 0, -60, 0,
                        MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.lege(anna, UUID.randomUUID(), "Grund", "", 0, -60, 0,
                        MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.lege(anna, UUID.randomUUID(), "Grund", WELT, 0, -60, 0,
                        null));
        assertThrows(IllegalArgumentException.class, () -> ablage.offeneVon(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.letzteVon(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.ungeleseneVon(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.offene(0));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.schreibe(null, Meldestand.OFFEN));
    }
}
