package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Kasse der Goldmark - der Riegel und die Weiterleitung.
 *
 * <p><b>KEIN geerbter {@link KasseVertrag} hier, und das ist eine Entscheidung gegen den
 * naheliegenden Weg</b> [19.11.2026]: Der Vertrag verlangt eine Kasse MIT Startguthaben
 * (seine erste Zusage ist "ein unbekannter Spieler hat das Startguthaben"), die Goldkasse
 * laesst sich aber nur ohne bauen. Eine geerbte Vertragsprobe haette darum die Kasse
 * DARUNTER gefragt und nicht die Goldkasse - gruen, ohne das Gepruefte zu beruehren.
 * Geprueft wird die Mechanik stattdessen dort, wo sie steht: im {@code SpeicherKasseTest}
 * und, auf den Goldmarktabellen, im {@code MariaGoldkasseTest}, der den vollen Vertrag ein
 * zweites Mal haelt.
 *
 * <p>Was HIER bleibt, kann nur hier stehen: dass der Riegel greift, und dass der Umschlag
 * jede der sechs Methoden an ihre eigene weiterreicht.
 */
@DisplayName("Die Kasse der Goldmark")
class GoldkasseTest {

    @Test
    @DisplayName("Eine Kasse MIT Startguthaben wird abgewiesen - beim Bau, nicht spaeter")
    void mitStartguthabenAbgewiesen() {
        // Beim START und nicht bei der ersten Buchung: Ein Betreiber, der das Plugin
        // falsch verdrahtet bekommt, soll es in der Startmeldung lesen und nicht Wochen
        // spaeter an einem Spieler mit 50.000 Goldmark, die niemand gekauft hat.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Goldkasse(new SpeicherKasse(50_000L)));

        assertTrue(fehler.getMessage().contains("50000"), fehler.getMessage());
        assertTrue(fehler.getMessage().contains("Startguthaben"), fehler.getMessage());
        assertTrue(fehler.getMessage().contains("Goldmark"),
                "die Meldung muss die WAEHRUNG nennen - sonst sucht der Betreiber am"
                        + " falschen Schluessel: " + fehler.getMessage());
    }

    @Test
    @DisplayName("Eine Kasse OHNE Startguthaben kommt durch")
    void ohneStartguthabenDurch() {
        // Die Gegenrichtung, und sie ist Pflicht: Ein Riegel, der ALLES abweist, waere
        // mit derselben Probe gruen wie einer, der das Richtige abweist.
        Goldkasse kasse = new Goldkasse(new SpeicherKasse(0L));

        assertEquals(0L, kasse.guthaben(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Ohne Kasse gibt es keine Goldkasse")
    void ohneKasse() {
        assertThrows(IllegalArgumentException.class, () -> new Goldkasse(null));
    }

    @Test
    @DisplayName("Der Riegel legt dabei KEIN Konto an")
    void riegelLegtNichtsAn() {
        Mitschrift darunter = new Mitschrift();

        new Goldkasse(darunter);

        // Der Riegel fragt mit einer Zufallskennung. Wuerde die Frage ein Konto anlegen,
        // stuende bei jedem Serverstart eine Zeile mehr in der Tabelle - und im Buch eine
        // Eroeffnungsbuchung fuer einen Spieler, den es nicht gibt.
        assertEquals(1, darunter.rufe.size(), darunter.rufe.toString());
        assertTrue(darunter.rufe.get(0).startsWith("guthaben"),
                "der Riegel darf NUR lesen: " + darunter.rufe);
    }

    @Test
    @DisplayName("Jede der sechs Methoden geht an ihre EIGENE weiter")
    void jedeMethodeAnIhreEigene() {
        UUID anna = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        Mitschrift darunter = new Mitschrift();
        Goldkasse kasse = new Goldkasse(darunter);
        darunter.rufe.clear();

        kasse.guthaben(anna);
        kasse.eroeffne(anna);
        kasse.abbuchen(anna, 4L, Buchungsgrund.WECHSEL_GOLDMARK_WEG);
        kasse.gutschreiben(anna, 10L, Buchungsgrund.WECHSEL_GOLDMARK_HER);
        kasse.verlauf(anna, 7);
        kasse.summeSeit(anna, Instant.EPOCH,
                Buchungsgrund.WECHSEL_GOLDMARK_HER);

        // DIE PROBE, DIE EIN VERTAUSCHEN FAENGT. Dass alle sechs Methoden DA sind, sichert
        // der Uebersetzer - Kasse hat keine Vorgabeumsetzungen. Was er NICHT sichert: dass
        // abbuchen auch abbuchen ruft und nicht gutschreiben. Ein solcher Dreher waere im
        // Betrieb Geld aus dem Nichts, und beide Buchungen saehen fuer sich richtig aus.
        //
        // Die ARGUMENTE stehen mit in der Mitschrift: Ein Umschlag, der den Betrag oder
        // den Grund vertauscht, kommt sonst durch.
        assertEquals(List.of(
                        "guthaben " + anna,
                        "eroeffne " + anna,
                        "abbuchen " + anna + " 4 wechsel_goldmark_weg",
                        "gutschreiben " + anna + " 10 wechsel_goldmark_her",
                        "verlauf " + anna + " 7",
                        "summeSeit " + anna + " " + Instant.EPOCH
                                + " wechsel_goldmark_her"),
                darunter.rufe);
    }

    @Test
    @DisplayName("Auch die ANTWORT kommt von darunter und nicht aus dem Umschlag")
    void antwortKommtVonDarunter() {
        Mitschrift darunter = new Mitschrift();
        Goldkasse kasse = new Goldkasse(darunter);
        darunter.naechsteAntwort = 42L;

        // Ein Umschlag, der eine eigene Zahl erfindet, waere eine zweite Wahrheit ueber
        // Geld. Geprueft mit einem Wert, den die Kasse darunter nie von sich aus gaebe.
        assertEquals(42L, kasse.guthaben(UUID.randomUUID()));
        assertEquals(42L, kasse.summeSeit(UUID.randomUUID(), Instant.EPOCH,
                Buchungsgrund.WECHSEL_GOLDMARK_HER));
        assertFalse(kasse.abbuchen(UUID.randomUUID(), 1L,
                        Buchungsgrund.WECHSEL_GOLDMARK_WEG),
                "ein false von darunter bleibt ein false");
    }

    @Test
    @DisplayName("Ein MARKGRUND wird abgewiesen - auf allen drei Wegen in das Buch")
    void markgrundAbgewiesen() {
        Mitschrift darunter = new Mitschrift();
        Goldkasse kasse = new Goldkasse(darunter);
        darunter.rufe.clear();
        UUID anna = UUID.randomUUID();

        // ALLE DREI WEGE, und das ist der Zweck dieser Probe: Ein Riegel, der nur an
        // abbuchen haengt, laesst die Gutschrift durch - und eine Gutschrift mit einem
        // Markgrund im Goldbuch ist genau die Zeile, die richtig aussieht und von der
        // falschen Waehrung erzaehlt.
        var beimAbbuchen = assertThrows(IllegalArgumentException.class,
                () -> kasse.abbuchen(anna, 1L, Buchungsgrund.TAGESBONUS));
        assertThrows(IllegalArgumentException.class,
                () -> kasse.gutschreiben(anna, 1L, Buchungsgrund.TAGESBONUS));
        assertThrows(IllegalArgumentException.class,
                () -> kasse.summeSeit(anna, Instant.EPOCH, Buchungsgrund.TAGESBONUS));

        assertTrue(beimAbbuchen.getMessage().contains("TAGESBONUS"),
                "die Meldung muss den GRUND nennen, sonst sucht niemand die Stelle: "
                        + beimAbbuchen.getMessage());
        // UND NICHTS IST ANGEKOMMEN. Ein Riegel, der wirft, nachdem er weitergereicht
        // hat, waere mit der Pruefung auf die Ausnahme allein gruen.
        assertEquals(List.of(), darunter.rufe,
                "der abgewiesene Grund darf die Kasse darunter nie erreichen");
    }

    @Test
    @DisplayName("Der Riegel laesst JEDEN Goldmarkgrund durch - auf allen drei Wegen")
    void goldmarkgruendeDurch() {
        // Die Gegenrichtung, und sie ist Pflicht: Ein Riegel, der ALLES abweist, waere
        // mit der Probe darueber genauso gruen.
        //
        // UEBER ALLE Gruende und nicht ueber eine Liste von Hand [23.11.2026]: Bis dahin
        // nannte diese Probe drei Gruende beim Namen, waehrend das Goldbuch schon sechs
        // hatte - ein neuer Grund lief an ihr vorbei. Jetzt laeuft jeder von selbst mit,
        // und die ZAHL unten faengt den Fall, dass die Auswahl nichts mehr trifft.
        Goldkasse kasse = new Goldkasse(new SpeicherKasse(0L));
        UUID anna = UUID.randomUUID();

        // Deckung fuer die Abzuege, mit einem Goldgrund gebucht.
        kasse.gutschreiben(anna, 1_000L, Buchungsgrund.WECHSEL_GOLDMARK_HER);
        long erwartet = 1_000L;

        int geprueft = 0;
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            if (grund.buch() != Buchungsgrund.Buch.GOLDMARK) {
                continue;
            }
            // JE RICHTUNG DER PASSENDE WEG, und das ist nicht Bequemlichkeit: Buchung
            // weist einen Betrag ab, der nicht zur Richtung des Grundes passt
            // ("Der Betrag -1 passt nicht zu WECHSEL_GOLDMARK_HER (EINNAHME)"). Dass der
            // Riegel der Goldkasse an ALLEN drei Wegen haengt, haelt die Probe darueber -
            // sie wirft denselben Markgrund in alle drei.
            if (grund.richtung() == Buchungsgrund.Richtung.EINNAHME) {
                kasse.gutschreiben(anna, 2L, grund);
                erwartet += 2L;
            } else {
                assertTrue(kasse.abbuchen(anna, 2L, grund), grund.name());
                erwartet -= 2L;
            }
            // Der dritte Weg in den Riegel - er prueft auch beim LESEN.
            kasse.summeSeit(anna, Instant.EPOCH, grund);
            geprueft++;
        }

        assertEquals(8, geprueft, "gemessen am 23.11.2026: acht Gruende im Goldbuch."
                + " Kommt einer dazu, ist das die Zeile, die es sagt");
        assertEquals(erwartet, kasse.guthaben(anna),
                "jede Buchung muss auch angekommen sein - sonst waere ein Riegel, der"
                        + " wirft NACHDEM er weitergereicht hat, hier gruen");
        // Die zwei Betreibergruende namentlich: Sie sind der Grund, aus dem es diese
        // Runde gab, und ein Verschieben in das Markbuch waere sonst stumm.
        assertEquals(Buchungsgrund.Buch.GOLDMARK,
                Buchungsgrund.BETREIBER_GOLDGUTSCHRIFT.buch());
        assertEquals(Buchungsgrund.Buch.GOLDMARK,
                Buchungsgrund.BETREIBER_GOLDABZUG.buch());
    }

    @Test
    @DisplayName("Ohne Grund gibt es keine Buchung - auch nicht im Goldbuch")
    void ohneGrund() {
        Goldkasse kasse = new Goldkasse(new SpeicherKasse(0L));

        assertThrows(IllegalArgumentException.class,
                () -> kasse.gutschreiben(UUID.randomUUID(), 1L, null));
    }

    /**
     * Eine Kasse, die mitschreibt, was man von ihr will.
     *
     * <p>Sie gibt fuer {@code guthaben} 0, damit der Riegel sie annimmt - mehr braucht
     * sie nicht zu koennen. Eine echte Kasse darunter waere hier das Falsche: Dann
     * stuende in der Mitschrift die WIRKUNG und nicht der RUF, und ein Umschlag, der
     * zweimal dieselbe Methode ruft, waere nicht von einem zu unterscheiden, der es
     * richtig macht.
     */
    private static final class Mitschrift implements Kasse {

        private final List<String> rufe = new ArrayList<>();

        /** Was der naechste zahlgebende Ruf antwortet - fuer die Probe der Rueckgabe. */
        private long naechsteAntwort;

        @Override
        public long guthaben(UUID spieler) {
            rufe.add("guthaben " + spieler);
            return naechsteAntwort;
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            rufe.add("eroeffne " + spieler);
            return true;
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            rufe.add("abbuchen " + spieler + " " + betrag + " " + grund.schluessel());
            return false;
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            rufe.add("gutschreiben " + spieler + " " + betrag + " " + grund.schluessel());
        }

        @Override
        public List<Buchung> verlauf(UUID spieler, int hoechstens) {
            rufe.add("verlauf " + spieler + " " + hoechstens);
            return List.of();
        }

        @Override
        public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
            rufe.add("summeSeit " + spieler + " " + ab + " " + grund.schluessel());
            return naechsteAntwort;
        }
    }
}
