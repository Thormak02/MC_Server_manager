package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Die Mietregel")
class MietregelTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 7);

    @Test
    @DisplayName("Am LETZTEN Tag gilt der Vertrag noch - abgelaufen ist er am Tag danach")
    void derLetzteTagGiltNoch() {
        // Die Festlegung, auf der alles beruht: laeuftBis ist EINGESCHLOSSEN. Ein
        // heute.isAfter(bis), das zu !heute.isBefore(bis) wird, nimmt jedem Spieler den
        // letzten bezahlten Tag.
        assertFalse(Mietregel.abgelaufen(HEUTE, HEUTE), "am letzten Tag noch gueltig");
        assertTrue(Mietregel.abgelaufen(HEUTE, HEUTE.plusDays(1)), "am Tag danach nicht");
        assertFalse(Mietregel.abgelaufen(HEUTE.plusDays(1), HEUTE));
    }

    @Test
    @DisplayName("Die Restlaufzeit ist 0 am letzten Tag und negativ danach")
    void restTageZaehltRichtig() {
        assertEquals(0L, Mietregel.restTage(HEUTE, HEUTE));
        assertEquals(29L, Mietregel.restTage(HEUTE.plusDays(29), HEUTE));
        assertEquals(-3L, Mietregel.restTage(HEUTE.minusDays(3), HEUTE),
                "seit drei Tagen abgelaufen");
    }

    @Test
    @DisplayName("Die Restlaufzeit rechnet ueber Monats- und Jahresgrenzen")
    void restTageUeberGrenzen() {
        // Eine Fassung, die Tage im Monat abzieht, waere bei zwei Tagen im selben Monat
        // gruen. Darum zwei Faelle, die beide eine Grenze ueberschreiten.
        assertEquals(30L, Mietregel.restTage(LocalDate.of(2026, 12, 7), HEUTE));
        assertEquals(55L, Mietregel.restTage(LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 11, 7)));
    }

    @Test
    @DisplayName("Ein frischer Vertrag ueber 30 Tage gilt an 30 Tagen")
    void abschlussDauertGenauSoLange() {
        // Das Minus eins in laeuftBisAb, und hier ist es nachgezaehlt: Ohne es waere jeder
        // Abschluss einen Tag zu lang. Gezaehlt wird, nicht gerechnet - sonst steht in der
        // Probe dieselbe Formel wie im Programm.
        LocalDate bis = Mietregel.laeuftBisAb(HEUTE, 30);

        int gueltigeTage = 0;
        for (LocalDate tag = HEUTE; !Mietregel.abgelaufen(bis, tag); tag = tag.plusDays(1)) {
            gueltigeTage++;
        }
        assertEquals(30, gueltigeTage);
    }

    @Test
    @DisplayName("Ein Vertrag ueber EINEN Tag gilt genau heute")
    void einTagIstEinTag() {
        // Der Rand, an dem ein Rechenfehler am groessten ist: Ohne das Minus eins waere
        // die Dauer hier das DOPPELTE der eingestellten.
        LocalDate bis = Mietregel.laeuftBisAb(HEUTE, 1);

        assertEquals(HEUTE, bis);
        assertFalse(Mietregel.abgelaufen(bis, HEUTE));
        assertTrue(Mietregel.abgelaufen(bis, HEUTE.plusDays(1)));
    }

    @Test
    @DisplayName("Verlaengern geht erst, wenn weniger als eine Mietdauer uebrig ist")
    void verlaengernErstGegenEnde() {
        // Ohne diese Sperre koennte ein reicher Spieler dreissigmal verlaengern und eine
        // Ladeneinheit fuer Jahre binden, ohne sie zu benutzen.
        assertFalse(Mietregel.darfVerlaengern(HEUTE.plusDays(30), HEUTE, 30),
                "genau eine Mietdauer uebrig - noch nicht");
        assertTrue(Mietregel.darfVerlaengern(HEUTE.plusDays(29), HEUTE, 30),
                "einen Tag weniger - jetzt schon");
    }

    @Test
    @DisplayName("Ein ABGELAUFENER Vertrag darf noch verlaengert werden")
    void abgelaufenDarfGerettetWerden() {
        // Gewollt: Zwischen dem Ablauf und dem taeglichen Lauf steht die Zeile noch da.
        // Wer in diesem Fenster verlaengert, rettet seinen Laden.
        assertTrue(Mietregel.darfVerlaengern(HEUTE.minusDays(1), HEUTE, 30));
    }

    @Test
    @DisplayName("Frueh verlaengern verliert die Resttage NICHT")
    void fruehVerlaengernBehaeltResttage() {
        // Gerechnet ab laeuftBis: Wer am zehnten von dreissig Tagen verlaengert, hat
        // danach sechzig Tage ab Beginn. Eine Fassung, die immer ab heute rechnet, waere
        // hier rot und wuerde dem Spieler zwanzig bezahlte Tage wegnehmen.
        LocalDate bis = HEUTE.plusDays(19);

        assertEquals(HEUTE.plusDays(49), Mietregel.verlaengert(bis, HEUTE, 30));
    }

    @Test
    @DisplayName("Spaet verlaengern bekommt die verstrichenen Tage NICHT geschenkt")
    void spaetVerlaengernRechnetAbHeute() {
        // Die andere Richtung desselben Ausdrucks, und sie braucht ihre eigene Probe: Eine
        // Fassung, die immer ab laeuftBis rechnet, waere bei der Zeile darueber gruen und
        // wuerde hier einem Spieler, der seit 100 Tagen weg war, einen Vertrag geben, der
        // in 70 Tagen schon abgelaufen ist.
        LocalDate bis = HEUTE.minusDays(100);

        assertEquals(HEUTE.plusDays(30), Mietregel.verlaengert(bis, HEUTE, 30));
    }

    @Test
    @DisplayName("Verlaengern am letzten Tag haengt genau eine Mietdauer an")
    void verlaengernAmLetztenTag() {
        // Der Uebergang zwischen den beiden Richtungen - hier sind laeuftBis und heute
        // derselbe Tag, und beide Zweige muessen dasselbe liefern.
        assertEquals(HEUTE.plusDays(30), Mietregel.verlaengert(HEUTE, HEUTE, 30));
    }

    @Test
    @DisplayName("Das Ende liegt nie mehr als zwei Mietdauern voraus")
    void nieWeiterAlsZweiDauern() {
        // Die Wirkung der Sperre, gezaehlt statt behauptet: Wer an JEDEM Tag versucht zu
        // verlaengern, kommt nie weiter als 2 x 30 - 1 Tage voraus.
        LocalDate bis = Mietregel.laeuftBisAb(HEUTE, 30);
        long weiteste = 0L;

        for (int tag = 0; tag < 400; tag++) {
            LocalDate jetzt = HEUTE.plusDays(tag);
            if (Mietregel.darfVerlaengern(bis, jetzt, 30)) {
                bis = Mietregel.verlaengert(bis, jetzt, 30);
            }
            weiteste = Math.max(weiteste, Mietregel.restTage(bis, jetzt));
        }

        assertEquals(59L, weiteste, "zwei Mietdauern minus den heutigen Tag");
    }

    @Test
    @DisplayName("Der volle Zeitraum ist Miete mal Tage")
    void vollerZeitraumRechnet() {
        assertEquals(15_000L, Mietregel.vollerZeitraum(500L, 30));
        assertEquals(500L, Mietregel.vollerZeitraum(500L, 1));
    }

    @Test
    @DisplayName("Eine Dauer von 0 oder mehr als einem Jahr ist keine")
    void dauergrenzen() {
        assertFalse(Mietregel.brauchbareDauer(0));
        assertTrue(Mietregel.brauchbareDauer(1));
        assertTrue(Mietregel.brauchbareDauer(Mietregel.HOECHSTE_DAUER));
        assertFalse(Mietregel.brauchbareDauer(Mietregel.HOECHSTE_DAUER + 1));
        assertFalse(Mietregel.brauchbareDauer(-1));
    }

    @Test
    @DisplayName("Eine unbrauchbare Dauer wirft, statt etwas zu rechnen")
    void unbrauchbareDauerWirft() {
        assertThrows(IllegalArgumentException.class,
                () -> Mietregel.laeuftBisAb(HEUTE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Mietregel.verlaengert(HEUTE, HEUTE, 0));
    }

    @Test
    @DisplayName("Ohne Tage ist nichts zu rechnen")
    void pflichtangaben() {
        assertThrows(IllegalArgumentException.class,
                () -> Mietregel.restTage(null, HEUTE));
        assertThrows(IllegalArgumentException.class,
                () -> Mietregel.restTage(HEUTE, null));
        assertThrows(IllegalArgumentException.class,
                () -> Mietregel.laeuftBisAb(null, 30));
    }

    @Test
    @DisplayName("Geraeumt wird, sobald die Ausfaelle die Schranke ERREICHEN")
    void raeumungsreifAbDerSchranke() {
        // Bei 3 gibt es ZWEI Warnungen und nicht drei: Der dritte Ausfall raeumt. Genau
        // diese Lesung steht am Namen der Einstellung, und hier ist sie gezaehlt.
        assertFalse(Mietregel.raeumungsreif(0, 3));
        assertFalse(Mietregel.raeumungsreif(1, 3));
        assertFalse(Mietregel.raeumungsreif(2, 3));
        assertTrue(Mietregel.raeumungsreif(3, 3));
    }

    @Test
    @DisplayName("Eine gesenkte Schranke raeumt auch, was schon darueber liegt")
    void gesenkteSchrankeRaeumtAuchDarueber() {
        // Der gemessene Fall fuer das >= statt ==: Der Betreiber senkt
        // laeden.ausfaelle-bis-raeumung von 5 auf 3, und ein Vertrag steht bei 4. Mit ==
        // ginge der Lauf an ihm vorbei - er stuende fuer immer in Verzug und wuerde nie
        // geraeumt, und die Halle waere voll mit Laeden, die niemand zahlt und niemand
        // bekommt.
        assertTrue(Mietregel.raeumungsreif(4, 3));
        assertTrue(Mietregel.raeumungsreif(99, 1));
    }

    @Test
    @DisplayName("Eine Schranke von 0 wird abgewiesen, die 1 nicht")
    void schrankeNullGehtNicht() {
        // Der Grund fuer das Verbot der 0 ist NICHT Haerte, sondern dass sie jeden
        // Vertrag trifft: raeumungsreif(0, 0) waere wahr, also auch fuer den heute
        // abgeschlossenen Vertrag ohne einen einzigen Ausfall. Der erste Lauf raeumte die
        // ganze Halle. Der TEXT wird geprueft und nicht nur der Wurf - eine Meldung, die
        // den falschen Grund nennt, schickt den Betreiber an die falsche Stelle.
        IllegalArgumentException fehler = assertThrows(IllegalArgumentException.class,
                () -> Mietregel.raeumungsreif(0, 0));

        assertTrue(fehler.getMessage().contains("auch der heute abgeschlossene"),
                fehler.getMessage());
        assertFalse(Mietregel.brauchbareSchranke(0));
        assertFalse(Mietregel.brauchbareSchranke(-1));
        // Die 1 IST erlaubt: "wer nicht zahlt, ist sofort weg" ist eine zulaessige
        // Serverregel. Dass der Spieler dann nie eine Warnung liest, steht als Zeile im
        // Bericht von Mietliste - Erkennung statt Verhinderung.
        assertTrue(Mietregel.brauchbareSchranke(1));
        assertTrue(Mietregel.raeumungsreif(1, 1), "bei 1 raeumt der erste Ausfall");
        assertFalse(Mietregel.raeumungsreif(0, 1), "ohne Ausfall aber nicht");
        assertTrue(Mietregel.brauchbareSchranke(Mietregel.HOECHSTE_SCHRANKE));
        assertFalse(Mietregel.brauchbareSchranke(Mietregel.HOECHSTE_SCHRANKE + 1));
    }
}
