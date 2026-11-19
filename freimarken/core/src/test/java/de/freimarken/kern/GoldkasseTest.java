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
        kasse.abbuchen(anna, 4L, Buchungsgrund.WOLKE_BIOM);
        kasse.gutschreiben(anna, 10L, Buchungsgrund.BESTAND);
        kasse.verlauf(anna, 7);
        kasse.summeSeit(anna, Instant.EPOCH, Buchungsgrund.BESTAND);

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
                        "abbuchen " + anna + " 4 wolke_biom",
                        "gutschreiben " + anna + " 10 bestand",
                        "verlauf " + anna + " 7",
                        "summeSeit " + anna + " " + Instant.EPOCH + " bestand"),
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
                Buchungsgrund.BESTAND));
        assertFalse(kasse.abbuchen(UUID.randomUUID(), 1L, Buchungsgrund.WOLKE_BIOM),
                "ein false von darunter bleibt ein false");
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
