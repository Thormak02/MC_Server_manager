package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Regel ueber die Wolkeninseln - wer eine bekommt und wer darauf bauen darf.
 *
 * <p>Geprueft wird, was ein Spieler merkt: Bekomme ich eine Insel, ohne irgendwo zu
 * stehen? Bekomme ich beim zweiten Mal eine ANDERE? Baut der Nachbar auf meiner? Und was
 * passiert, wenn die Ablage schweigt?
 *
 * <p><b>Der Unterschied zum {@link InselbuchTest} steckt in der ersten Probe</b>: Dort
 * nimmt man die Insel, auf der man STEHT. Hier gibt es nichts, worauf man stehen koennte -
 * das Buch sucht das Feld, und zwar immer dasselbe fuer denselben Zustand der Welt.
 */
@DisplayName("Die Regel ueber die Wolkeninseln")
class WolkenbuchTest {

    private static final String WELT = "freimarken:wolkenwerder";

    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-18T12:00:00Z"), java.time.ZoneOffset.UTC);

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private SpeicherLager lager;
    private SpeicherKasse kasse;
    private Wolkenbuch buch;

    @BeforeEach
    void vorher() {
        lager = new SpeicherLager();
        kasse = new SpeicherKasse(1_000_000L, UHR);
        buch = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager, kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
    }

    /** Eine Weltkoordinate mitten auf dem Feld dieser Nummer. */
    private int mitte(int feld) {
        return buch.plan().feldMitte(feld);
    }

    // ===================================================== Nehmen

    @Test
    @DisplayName("Wer eine Insel will, bekommt ein FELD - ohne irgendwo zu stehen")
    void nehmenOhneZuStehen() {
        // Die Probe, die den Unterschied zu Hafenwerder festhaelt: Dort ist der Ort das
        // Argument, hier gibt es keinen. In einer Welt aus Leere kann niemand auf dem
        // Feld stehen, das er haben will.
        Wolkenbuch.Entscheid entscheid = buch.nehmen(WELT, anna, 1);

        assertTrue(entscheid.erteilt(), entscheid.bescheid().toString());
        assertEquals(0L, entscheid.kosten(), "gratis - Plan 6.2");
        Parzelle insel = entscheid.insel().orElseThrow();
        assertTrue(insel.istWolke());
        assertEquals(anna, insel.eigentuemer());
        assertEquals(1_000_000L, kasse.guthaben(anna),
                "und das Konto ist unberuehrt - 0 abgebucht ist 0");
    }

    @Test
    @DisplayName("Das erste Feld ist NICHT das Ankunftsfeld")
    void ersteInselMeidetDasAnkunftsfeld() {
        // Die Ringsuche faengt in der Mitte an, und in der Mitte liegt Feld 0/0 - der
        // Platz, auf dem jeder landet. Ohne diesen Riegel bekaeme der erste Spieler des
        // Servers die Ankunftsplattform, und jeder weitere landete auf seinem Grund.
        Parzelle insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();

        assertFalse(buch.plan().istAnkunftsfeld(insel.x(), insel.z()),
                "bekommen: " + insel.x() + "/" + insel.z());
    }

    @Test
    @DisplayName("Zwei Spieler bekommen VERSCHIEDENE Felder")
    void zweiSpielerZweiFelder() {
        Parzelle eine = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
        Parzelle andere = buch.nehmen(WELT, bert, 1).insel().orElseThrow();

        assertTrue(eine.x() != andere.x() || eine.z() != andere.z(),
                "beide bekamen " + eine.x() + "/" + eine.z());
        assertEquals(anna, buch.inselAn(WELT, mitte(eine.x()), mitte(eine.z()))
                .orElseThrow().eigentuemer());
        assertEquals(bert, buch.inselAn(WELT, mitte(andere.x()), mitte(andere.z()))
                .orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Die Felder liegen nah beieinander - ringweise von der Mitte")
    void ringweiseVonInnen() {
        // Die Entscheidung ueber die ganze Welt: Bei Zufall laegen die ersten zwanzig
        // Felder ueber tausende Bloecke verstreut, mit Platten voller erzeugter Chunks
        // dazwischen - erzeugt wird nur, wo jemand ist.
        for (int i = 0; i < 8; i++) {
            Parzelle insel = buch.nehmen(WELT, UUID.randomUUID(), 1).insel()
                    .orElseThrow();

            assertTrue(Math.max(Math.abs(insel.x()), Math.abs(insel.z())) <= 1,
                    "die ersten acht Inseln liegen im ersten Ring um das Ankunftsfeld,"
                            + " nicht bei " + insel.x() + "/" + insel.z());
        }
    }

    @Test
    @DisplayName("Wer schon genug hat, bekommt keine - und die Suche laeuft nicht")
    void grenzeErreicht() {
        buch.nehmen(WELT, anna, 1);

        Wolkenbuch.Entscheid zweite = buch.nehmen(WELT, anna, 1);

        assertEquals(Wolkenbuch.Bescheid.GRENZE_ERREICHT, zweite.bescheid());
        assertTrue(zweite.insel().isEmpty());
        assertEquals(1, lager.anzahlVon(WELT, anna), "und es blieb bei einer");
    }

    @Test
    @DisplayName("Mit hoechstzahl 2 bekommt derselbe Spieler eine zweite")
    void zweiteMitRecht() {
        Parzelle eine = buch.nehmen(WELT, anna, 2).insel().orElseThrow();
        Parzelle zweite = buch.nehmen(WELT, anna, 2).insel().orElseThrow();

        assertTrue(eine.id() != zweite.id());
        assertEquals(2, buch.anzahlVon(WELT, anna));
        assertEquals(2, buch.inselnVon(WELT, anna).size());
    }

    @Test
    @DisplayName("Mit hoechstzahl 0 bekommt niemand eine")
    void hoechstzahlNull() {
        // Der Weg, Wolkenwerder zuzumachen, ohne das Recht anzufassen.
        Wolkenbuch.Entscheid entscheid = buch.nehmen(WELT, anna, 0);

        assertEquals(Wolkenbuch.Bescheid.GRENZE_ERREICHT, entscheid.bescheid());
        assertEquals(0, lager.anzahlVon(WELT, anna));
    }

    @Test
    @DisplayName("Eine volle Welt sagt es - und bucht nichts ab")
    void weltVoll() {
        // Die kleinste moegliche Welt: zwei Felder je Achse, davon eines das
        // Ankunftsfeld. Nach drei Inseln ist Schluss.
        Wolkenplan plan = Wolkenplan.wolkenwerder();
        Wolkenbuch klein = new Wolkenbuch(
                Wolkengrenze.ausKante(plan, Wolkengrenze.mindestKante(plan)), lager,
                kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        for (int i = 0; i < 3; i++) {
            assertTrue(klein.nehmen(WELT, UUID.randomUUID(), 1).erteilt(),
                    "Insel " + (i + 1) + " muss noch gehen");
        }
        Wolkenbuch.Entscheid vierte = klein.nehmen(WELT, anna, 1);

        assertEquals(Wolkenbuch.Bescheid.WELT_VOLL, vierte.bescheid());
        assertEquals(1_000_000L, kasse.guthaben(anna), "und nichts abgebucht");
    }

    @Test
    @DisplayName("Reicht das Geld nicht, gibt es keine Insel und keine Buchung")
    void zuWenigMark() {
        // Ab Werk ist eine Insel gratis, dieser Zweig also unerreichbar - darum wird er
        // mit einem PREIS geprueft. Ein Riegel, den nichts ausloest, ist gruen mit und
        // ohne.
        Wolkenbuch teuer = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager,
                new SpeicherKasse(100L, UHR),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitWolken(
                        Wolkenwerte.VORGABE.mitPreis(5_000L))));

        Wolkenbuch.Entscheid entscheid = teuer.nehmen(WELT, anna, 1);

        assertEquals(Wolkenbuch.Bescheid.ZU_WENIG_MARK, entscheid.bescheid());
        assertEquals(5_000L, entscheid.kosten(), "die Absage nennt den Preis");
        assertEquals(0, lager.anzahlVon(WELT, anna));
    }

    @Test
    @DisplayName("Der Preis kommt beim GEBRAUCH aus dem Stand, nicht vom Anfang")
    void preisKommtAusDemStand() {
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Wolkenbuch mitStand = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager, kasse,
                stand);
        assertEquals(0L, mitStand.preis());

        stand.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitPreis(5_000L).mitHoechstzahl(4)));

        assertEquals(5_000L, mitStand.preis(), "ein Neuladen muss den Preis erreichen");
        assertEquals(4, mitStand.hoechstzahl());
        assertEquals(5_000L, mitStand.nehmen(WELT, anna, 1).kosten(),
                "und die Abbuchung nimmt den neuen");
    }

    @Test
    @DisplayName("Die Starttruhe kommt ebenfalls aus dem Stand")
    void starttruheKommtAusDemStand() {
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Wolkenbuch mitStand = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager, kasse,
                stand);
        assertEquals(Wolkenwerte.VORGABE_STARTTRUHE, mitStand.starttruhe());

        stand.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitStarttruhe(Map.of("ice", 1))));

        assertEquals(Map.of("ice", 1), mitStand.starttruhe(),
                "eine gemerkte Truhe gaebe dem naechsten Spieler den Inhalt von gestern");
    }

    @Test
    @DisplayName("Schweigt die Ablage, ist nichts passiert - auch nicht am Konto")
    void gestoert() {
        Wolkenbuch stumm = new Wolkenbuch(Wolkengrenze.wolkenwerder(),
                new WerfendesLager(), kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        Wolkenbuch.Entscheid entscheid = stumm.nehmen(WELT, anna, 1);

        assertEquals(Wolkenbuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(1_000_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Schlaegt das Eintragen fehl, kommt das Geld ZURUECK")
    void gegenbuchung() {
        // Nicht theoretisch: Zwei Spieler, die im selben Augenblick tippen, bekommen aus
        // der Ringsuche DASSELBE freie Feld - sie stehen dafuer nicht einmal am selben
        // Ort. Den zweiten laesst der eindeutige Schluessel auflaufen.
        Wolkenbuch teuer = new Wolkenbuch(Wolkengrenze.wolkenwerder(),
                new NachDemLesenWerfendesLager(), kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitWolken(
                        Wolkenwerte.VORGABE.mitPreis(5_000L))));

        Wolkenbuch.Entscheid entscheid = teuer.nehmen(WELT, anna, 1);

        assertEquals(Wolkenbuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(1_000_000L, kasse.guthaben(anna),
                "ohne Gegenbuchung zahlte er 5.000 Mark fuer nichts");
    }

    // ===================================================== Bauen

    @Test
    @DisplayName("In der LEERE baut niemand - auch nicht der Nachbar")
    void inDerLeereBautNiemand() {
        Parzelle insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
        int imLeeren = buch.plan().feldAnfang(insel.x()) + buch.plan().feldKante() + 5;

        assertFalse(buch.darfBauen(WELT, anna, imLeeren, mitte(insel.z())),
                "ein Block in der Leere ist der erste Block einer Bruecke");
        assertTrue(buch.inselAn(WELT, imLeeren, mitte(insel.z())).isEmpty(),
                "und dort liegt gar kein Besitz - feldNummer nennte sonst das Feld davor");
    }

    @Test
    @DisplayName("Auf der eigenen Insel baut der Eigentuemer, auf einer fremden nicht")
    void eigeneUndFremde() {
        Parzelle insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
        int x = mitte(insel.x());
        int z = mitte(insel.z());

        assertTrue(buch.darfBauen(WELT, anna, x, z));
        assertFalse(buch.darfBauen(WELT, bert, x, z));
        assertFalse(buch.darfBauen(WELT, null, x, z),
                "ohne Spieler ist die Antwort nein - und sie WIRFT nicht");
    }

    @Test
    @DisplayName("Auf einem freien Feld baut niemand, auch nicht auf dem Ankunftsfeld")
    void freiesFeldUndAnkunft() {
        int ankunft = buch.plan().ankunftsMitte();

        assertFalse(buch.darfBauen(WELT, anna, ankunft, ankunft));
        assertFalse(buch.darfBauen(WELT, anna, mitte(5), mitte(5)),
                "Feld 5/5 ist nie vergeben worden");
    }

    // ===================================================== Mitbauer

    @Test
    @DisplayName("Der Eigentuemer traegt einen Mitbauer ein - und der darf bauen")
    void mitbauerDazu() {
        Parzelle insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
        int x = mitte(insel.x());
        int z = mitte(insel.z());

        Wolkenbuch.Mitentscheid entscheid =
                buch.mitbauerHinzufuegen(WELT, anna, x, z, bert);

        assertTrue(entscheid.erteilt(), entscheid.bescheid().toString());
        assertEquals(Set.of(bert), entscheid.insel().orElseThrow().mitbauer());
        assertTrue(buch.darfBauen(WELT, bert, x, z),
                "die Zusage ist wertlos, wenn er danach nicht bauen darf");
    }

    @Test
    @DisplayName("Austragen nimmt ihm das Bauen wieder")
    void mitbauerWeg() {
        Parzelle insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
        int x = mitte(insel.x());
        int z = mitte(insel.z());
        buch.mitbauerHinzufuegen(WELT, anna, x, z, bert);

        assertTrue(buch.mitbauerEntfernen(WELT, anna, x, z, bert).erteilt());

        assertFalse(buch.darfBauen(WELT, bert, x, z));
        assertEquals(Set.of(), buch.inselAn(WELT, x, z).orElseThrow().mitbauer());
    }

    @Test
    @DisplayName("Zweimal eintragen sagt es - und zweimal austragen auch")
    void schonUndNichtDabei() {
        // Zwei eigene Bescheide und nicht dieselbe Bestaetigung: Wer "eingetragen" liest,
        // obwohl nichts geschehen ist, haelt seine Liste fuer laenger, als sie ist.
        Parzelle insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
        int x = mitte(insel.x());
        int z = mitte(insel.z());
        buch.mitbauerHinzufuegen(WELT, anna, x, z, bert);

        assertEquals(Wolkenbuch.Mitbescheid.SCHON_EINGETRAGEN,
                buch.mitbauerHinzufuegen(WELT, anna, x, z, bert).bescheid());
        buch.mitbauerEntfernen(WELT, anna, x, z, bert);
        assertEquals(Wolkenbuch.Mitbescheid.NICHT_EINGETRAGEN,
                buch.mitbauerEntfernen(WELT, anna, x, z, bert).bescheid());
    }

    @Test
    @DisplayName("Der Eigentuemer selbst gilt als schon eingetragen")
    void sichSelbst() {
        Parzelle insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();

        assertEquals(Wolkenbuch.Mitbescheid.SCHON_EINGETRAGEN,
                buch.mitbauerHinzufuegen(WELT, anna, mitte(insel.x()),
                        mitte(insel.z()), anna).bescheid(),
                "er darf ohnehin alles - ein Eintrag waere eine Zeile ohne Wirkung");
    }

    @Test
    @DisplayName("Auf einer fremden Insel setzt niemand Mitbauer")
    void fremdeInsel() {
        Parzelle insel = buch.nehmen(WELT, anna, 1).insel().orElseThrow();
        int x = mitte(insel.x());
        int z = mitte(insel.z());

        Wolkenbuch.Mitentscheid entscheid =
                buch.mitbauerHinzufuegen(WELT, bert, x, z, bert);

        assertEquals(Wolkenbuch.Mitbescheid.NICHT_EIGENTUEMER, entscheid.bescheid());
        assertEquals(anna, entscheid.insel().orElseThrow().eigentuemer(),
                "und die Meldung kann den Besitzer nennen");
        assertEquals(Set.of(), buch.inselAn(WELT, x, z).orElseThrow().mitbauer());
    }

    @Test
    @DisplayName("In der Leere gibt es keine Insel, auf der man Mitbauer setzen koennte")
    void mitbauerInDerLeere() {
        int imLeeren = buch.plan().feldKante() + 5;

        assertEquals(Wolkenbuch.Mitbescheid.KEINE_INSEL,
                buch.mitbauerHinzufuegen(WELT, anna, imLeeren, imLeeren, bert)
                        .bescheid());
    }

    @Test
    @DisplayName("Schweigt die Ablage beim Mitbauer, ist es GESTOERT und kein Wurf")
    void mitbauerGestoert() {
        Wolkenbuch stumm = new Wolkenbuch(Wolkengrenze.wolkenwerder(),
                new WerfendesLager(), kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        assertEquals(Wolkenbuch.Mitbescheid.GESTOERT,
                stumm.mitbauerHinzufuegen(WELT, anna, 0, 0, bert).bescheid());
        assertEquals(Wolkenbuch.Mitbescheid.GESTOERT,
                stumm.mitbauerEntfernen(WELT, anna, 0, 0, bert).bescheid());
    }

    @Test
    @DisplayName("Ohne Grenze gibt es kein Buch")
    void ohneGrenze() {
        assertThrows(IllegalArgumentException.class,
                () -> new Wolkenbuch(null, lager, kasse,
                        new Wirtschaftsstand(Wirtschaftswerte.VORGABE)));
    }

    /** Ein Lager, das bei JEDER Frage wirft - fuer den Ausfallzweig. */
    private static final class WerfendesLager extends Lagerattrappe {

        @Override
        public java.util.Optional<Parzelle> finde(String welt, int x, int z) {
            throw new IllegalStateException("Ablage stumm");
        }

        @Override
        public int anzahlVon(String welt, UUID spieler) {
            throw new IllegalStateException("Ablage stumm");
        }

        @Override
        public List<Parzelle> besitzIn(String welt, Art art) {
            throw new IllegalStateException("Ablage stumm");
        }
    }

    /** Ein Lager, das LESEN kann und beim Schreiben wirft - fuer die Gegenbuchung. */
    private static final class NachDemLesenWerfendesLager extends Lagerattrappe {

        @Override
        public Parzelle wolkeAnlegen(String welt, int feldX, int feldZ, UUID eigentuemer,
                                     long preis) {
            throw new IllegalStateException("Feld schon vergeben");
        }
    }
}
