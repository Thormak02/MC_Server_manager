package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Goldkasse;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.SpeicherKasse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Geld von Hand - die Regel, ohne Server.
 *
 * <p>Jeder der fuenf Ausgaenge bekommt seine Probe, und zwei davon sind der Grund, aus
 * dem es diese Klasse gibt: Ein unbekannter Name darf NICHT buchen (die Kasse legt sonst
 * ein Konto samt Startguthaben an), und ein ungedeckter Abzug darf nicht als Erfolg
 * gemeldet werden.
 */
@DisplayName("Geld von Hand")
class BetreibergeldTest {

    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());

    private SpeicherKasse kasse;

    @BeforeEach
    void vorher() {
        kasse = new SpeicherKasse(0L, UHR);
    }

    /** Die Namenssuche der Proben: genau ein bekannter Name. */
    private static Betreibergeld.Namenssuche nur(String bekannt, UUID kennung) {
        return name -> bekannt.equals(name) ? kennung : null;
    }

    @Test
    @DisplayName("Eine positive Zahl schreibt gut - und nennt den Stand danach")
    void positivSchreibtGut() {
        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", 500L);

        assertEquals(Betreibergeld.Ausgang.GUTGESCHRIEBEN, ergebnis.ausgang());
        assertEquals(500L, ergebnis.betrag(), "der Betrag OHNE Vorzeichen");
        assertEquals(500L, ergebnis.stand());
        assertEquals(500L, kasse.guthaben(ANNA));
        // NEUESTE ZUERST, und die EROEFFNUNG gehoert dazu: Die Kasse eroeffnet das
        // Konto bei der ersten Buchung selbst und schreibt dafuer ihre eigene Zeile -
        // "keine Geldbewegung ohne Zeile im Buch" gilt auch fuer das Startguthaben.
        assertEquals(List.of(Buchungsgrund.BETREIBER_GUTSCHRIFT,
                        Buchungsgrund.EROEFFNUNG),
                gruende(kasse.verlauf(ANNA, 10)),
                "ein EIGENER Grund: Mehrere vorhandene werden als Marke gelesen"
                        + " (\"ist heute schon ein Bonus gezahlt?\")");
    }

    @Test
    @DisplayName("Ein Minus zieht ab - und bucht mit dem anderen Grund")
    void minusZiehtAb() {
        kasse.gutschreiben(ANNA, 1_000L, Buchungsgrund.BESTAND);

        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", -400L);

        assertEquals(Betreibergeld.Ausgang.ABGEZOGEN, ergebnis.ausgang());
        assertEquals(400L, ergebnis.betrag(), "der Betrag OHNE Vorzeichen - die Richtung"
                + " steht im Ausgang und im Grund");
        assertEquals(600L, ergebnis.stand());
        assertEquals(List.of(Buchungsgrund.BETREIBER_ABZUG, Buchungsgrund.BESTAND,
                        Buchungsgrund.EROEFFNUNG),
                gruende(kasse.verlauf(ANNA, 10)),
                "neueste zuerst - der Abzug steht oben");
    }

    @Test
    @DisplayName("Ein unbekannter Name bucht NICHT - und legt kein Konto an")
    void unbekannterNameBuchtNicht() {
        // DER WICHTIGSTE DER FUENF: getOfflinePlayer(String) erfindet fuer einen
        // unbekannten Namen eine frische Kennung, und gutschreiben legt dafuer ein Konto
        // samt Startguthaben an. Ein Tippfehler schickte das Geld auf ein Geisterkonto,
        // und nichts waere aufgefallen.
        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Ann", 500L);

        assertEquals(Betreibergeld.Ausgang.UNBEKANNTER_SPIELER, ergebnis.ausgang());
        assertEquals("Ann", ergebnis.spieler(), "der getippte Name gehoert in die"
                + " Antwort - sonst sucht der Betreiber den Fehler anderswo");
        assertEquals(0L, ergebnis.betrag());
        assertEquals(0L, kasse.guthaben(ANNA));
        assertTrue(kasse.verlauf(ANNA, 10).isEmpty(), "keine Buchung");
    }

    @Test
    @DisplayName("Der NAME wird vor dem Betrag geprueft")
    void nameVorBetrag() {
        // Wer sich im Namen vertippt UND eine 0 schreibt, soll den Namen lesen: Jener
        // ist der Fehler, der auf ein Geisterkonto fuehrt.
        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Ann", 0L);

        assertEquals(Betreibergeld.Ausgang.UNBEKANNTER_SPIELER, ergebnis.ausgang());
    }

    @Test
    @DisplayName("0 Mark ist keine Buchung")
    void nullIstKeineBuchung() {
        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", 0L);

        assertEquals(Betreibergeld.Ausgang.NICHTS_ZU_BUCHEN, ergebnis.ausgang());
        assertTrue(kasse.verlauf(ANNA, 10).isEmpty(),
                "eine Zeile ueber 0 Mark im Buch ist eine Zeile ohne Geldbewegung");
    }

    @Test
    @DisplayName("Ein ungedeckter Abzug meldet es - und bucht nichts")
    void ungedeckterAbzug() {
        kasse.gutschreiben(ANNA, 100L, Buchungsgrund.BESTAND);

        var ergebnis = Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", -500L);

        assertEquals(Betreibergeld.Ausgang.NICHT_GEDECKT, ergebnis.ausgang());
        assertEquals(500L, ergebnis.betrag(), "was er wollte - damit der Betreiber die"
                + " Zahl mit dem Stand vergleichen kann");
        assertEquals(100L, ergebnis.stand(), "und der Stand, der nicht reicht");
        assertEquals(100L, kasse.guthaben(ANNA), "nichts passiert");
        assertEquals(List.of(Buchungsgrund.BESTAND, Buchungsgrund.EROEFFNUNG),
                gruende(kasse.verlauf(ANNA, 10)),
                "keine Zeile vom abgewiesenen Abzug - false heisst: nichts passiert");
    }

    @Test
    @DisplayName("Eine stumme Kasse wirft weiter - der Aufrufer protokolliert")
    void stummeKasseWirftWeiter() {
        // Kein Rueckfall: Der Betreiber haelt die Korrektur sonst fuer erledigt und die
        // naechste fuer eine zweite.
        Kasse stumm = new Kasse() {
            @Override
            public long guthaben(UUID spieler) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public boolean eroeffne(UUID spieler) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public List<Buchung> verlauf(UUID spieler, int hoechstens) {
                throw new IllegalStateException("die Kasse schweigt");
            }

            @Override
            public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
                throw new IllegalStateException("die Kasse schweigt");
            }
        };

        assertThrows(IllegalStateException.class,
                () -> Betreibergeld.buche(stumm, nur("Anna", ANNA), "Anna", 500L));
    }

    @Test
    @DisplayName("Kasse und Namenssuche sind Pflicht")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> Betreibergeld.buche(null, nur("Anna", ANNA), "Anna", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> Betreibergeld.buche(kasse, null, "Anna", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> Betreibergeld.bucheGold(null, nur("Anna", ANNA), "Anna", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> Betreibergeld.bucheGold(new Goldkasse(kasse), null, "Anna", 1L));
    }

    @Test
    @DisplayName("bucheGold schreibt den GOLDgrund - in die Goldkasse")
    void goldSchreibtDenGoldgrund() {
        // Die erste Buchung, die Goldmark von AUSSEN in das Buch bringt [23.11.2026].
        // Plan 8.1 nennt zwei Quellen - Tausch aus Mark und Echtgeld; die zweite war
        // nie gebaut, und eine verlorene Goldmark war nur von Hand in der Datenbank zu
        // ersetzen - genau der Weg, gegen den diese Klasse gebaut wurde.
        Goldkasse gold = new Goldkasse(kasse);

        var ergebnis = Betreibergeld.bucheGold(gold, nur("Anna", ANNA), "Anna", 40L);

        assertEquals(Betreibergeld.Ausgang.GUTGESCHRIEBEN, ergebnis.ausgang());
        assertEquals(40L, ergebnis.stand());
        assertEquals(40L, gold.guthaben(ANNA));
        // Der Grund kommt aus dem VERLAUF der Kasse darunter und nicht aus
        // Betreibergeld - sonst holte die Probe ihre Erwartung aus dem Gepruefen.
        assertEquals(List.of(Buchungsgrund.BETREIBER_GOLDGUTSCHRIFT,
                        Buchungsgrund.EROEFFNUNG),
                gruende(kasse.verlauf(ANNA, 10)));
    }

    @Test
    @DisplayName("Ein Minus in Goldmark zieht ab - mit dem Goldabzug")
    void goldMinusZiehtAb() {
        Goldkasse gold = new Goldkasse(kasse);
        gold.gutschreiben(ANNA, 100L, Buchungsgrund.WECHSEL_GOLDMARK_HER);

        var ergebnis = Betreibergeld.bucheGold(gold, nur("Anna", ANNA), "Anna", -40L);

        assertEquals(Betreibergeld.Ausgang.ABGEZOGEN, ergebnis.ausgang());
        assertEquals(40L, ergebnis.betrag(), "der Betrag OHNE Vorzeichen");
        assertEquals(60L, ergebnis.stand());
        assertEquals(List.of(Buchungsgrund.BETREIBER_GOLDABZUG,
                        Buchungsgrund.WECHSEL_GOLDMARK_HER, Buchungsgrund.EROEFFNUNG),
                gruende(kasse.verlauf(ANNA, 10)));
    }

    @Test
    @DisplayName("Der Markweg schreibt NIE einen Goldgrund")
    void markwegSchreibtKeinenGoldgrund() {
        // DIE GEGENRICHTUNG, und ohne sie waere ein Weg, der immer den Goldgrund nimmt,
        // mit den zwei Proben darueber genauso gruen. Gefragt wird nach dem BUCH und
        // nicht mit gehoertIn: Die EROEFFNUNG der Kasse gehoert in JEDES Buch, und
        // gehoertIn gaebe fuer sie richtigerweise true.
        Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", 40L);
        Betreibergeld.buche(kasse, nur("Anna", ANNA), "Anna", -10L);

        List<Buchungsgrund> gebucht = gruende(kasse.verlauf(ANNA, 10));
        assertEquals(3, gebucht.size(), "Gutschrift, Abzug, Eroeffnung");
        for (Buchungsgrund grund : gebucht) {
            assertNotEquals(Buchungsgrund.Buch.GOLDMARK, grund.buch(),
                    "ein Markweg darf keinen Goldgrund schreiben: " + grund);
        }
    }

    @Test
    @DisplayName("Jede Waehrung fuehrt ihr Befehlswort UND ihre Schranke")
    void waehrungFuehrtWortUndSchranke() {
        // Es ist ein PAAR: Wer das Wort an einer Stelle fuehrt und die Zahl an einer
        // anderen, laesst sie auseinanderlaufen - dann nimmt Brigadier den Befehl nicht
        // an und antwortet englisch an eine Konsole, die ihre Antwort nicht zurueckgibt.
        assertEquals("geld", Betreibergeld.Waehrung.MARK.befehlswort());
        assertEquals("goldgeld", Betreibergeld.Waehrung.GOLDMARK.befehlswort());
        assertEquals(Betreibergeld.HOECHSTENS,
                Betreibergeld.Waehrung.MARK.hoechstens());
        assertEquals(Betreibergeld.HOECHSTENS_GOLD,
                Betreibergeld.Waehrung.GOLDMARK.hoechstens());
        // DER DRITTE TEIL DES PAARS [23.11.2026]: Wo die Buchung danach steht. Die
        // Hilfezeile nannte fuer beide Waehrungen /mark verlauf - dort kommen aber nur
        // Markzeilen an (ParzellenBefehle.verlaufZeilen gibt Buch.MARK herein), das
        // Goldbuch liest nur /mark muenzhaus verlauf. Der Betreiber bucht 40 Goldmark
        // zurueck, sieht im Markverlauf nichts und bucht noch einmal.
        assertEquals("/mark verlauf", Betreibergeld.Waehrung.MARK.verlaufsbefehl());
        assertEquals("/mark muenzhaus verlauf",
                Betreibergeld.Waehrung.GOLDMARK.verlaufsbefehl());
        // UND SIE MUESSEN SICH UNTERSCHEIDEN - sonst ist die Zusage oben nur zweimal
        // dieselbe Zeichenkette, und genau das war der Fehler.
        assertNotEquals(Betreibergeld.Waehrung.MARK.verlaufsbefehl(),
                Betreibergeld.Waehrung.GOLDMARK.verlaufsbefehl(),
                "beide Waehrungen zeigen auf dasselbe Buch");

        // Und JEDE Waehrung hat alles drei - eine neue ohne Wort, ohne Zahl oder ohne
        // Verlauf faellt auf.
        int geprueft = 0;
        for (Betreibergeld.Waehrung waehrung : Betreibergeld.Waehrung.values()) {
            assertFalse(waehrung.befehlswort().isBlank(), waehrung.name());
            assertTrue(waehrung.hoechstens() > 0L, waehrung.name());
            assertTrue(waehrung.verlaufsbefehl().startsWith("/"), waehrung.name());
            geprueft++;
        }
        assertEquals(Betreibergeld.Waehrung.values().length, geprueft);
    }

    @Test
    @DisplayName("Die Schranke ist eine Zahl, die kein Konto ueberlaufen laesst")
    void schrankeIstTechnisch() {
        // Keine Spielzahl: Sie faengt den Tippfehler mit drei Nullen zu viel. Zwei
        // Buchungen in dieser Groesse muessen sich noch addieren lassen.
        assertTrue(Betreibergeld.HOECHSTENS > 0L);
        assertTrue(Long.MAX_VALUE / Betreibergeld.HOECHSTENS > 1_000_000L,
                "sonst kann eine Reihe solcher Buchungen die Summe im Buch sprengen");
        // Die GOLDschranke muss viel kleiner sein: 1 Goldmark ist 800 Mark (Plan 8.1),
        // eine Milliarde Goldmark waeren also achthundert Milliarden Mark. Die
        // Markschranke hier zu uebernehmen waere der eigentliche Schaden gewesen.
        assertTrue(Betreibergeld.HOECHSTENS_GOLD > 0L);
        assertTrue(Betreibergeld.HOECHSTENS_GOLD < Betreibergeld.HOECHSTENS,
                "eine Milliarde Goldmark sind kein Tippfehler mehr, sondern ein Vermoegen");
        assertTrue(Long.MAX_VALUE / Betreibergeld.HOECHSTENS_GOLD > 1_000_000L,
                "sonst kann eine Reihe solcher Buchungen die Summe im Buch sprengen");
    }

    private static List<Buchungsgrund> gruende(List<Buchung> zeilen) {
        return zeilen.stream().map(Buchung::grund).toList();
    }
}
