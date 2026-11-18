package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Regel ueber die Meerinseln - wer eine nehmen darf und wer darauf bauen darf.
 *
 * <p>Geprueft wird, was ein Spieler merkt: Bekomme ich die Insel, auf der ich stehe?
 * Kostet mich ein Fehlschlag Geld? Baut der Nachbar auf meiner Insel? Und was passiert,
 * wenn die Ablage schweigt?
 *
 * <p><b>Die Ankunftsinsel und das offene Meer sind KEINE Randfaelle</b>, sondern die
 * zwei Stellen, an denen die Geometrie entscheidet, bevor die Ablage ueberhaupt gefragt
 * wird. Zwei Proben halten darum ausdruecklich fest, dass das Lager dabei stumm bleiben
 * DARF - mit einem Lager, das beim Fragen wirft.
 */
@DisplayName("Die Regel ueber die Meerinseln")
class InselbuchTest {

    private static final String WELT = "freimarken:hafenwerder";

    private static final long PREIS = Inselwerte.VORGABE_PREIS;

    private static final java.time.Clock UHR = java.time.Clock.fixed(
            Instant.parse("2026-11-18T12:00:00Z"), java.time.ZoneOffset.UTC);

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private SpeicherLager lager;
    private SpeicherKasse kasse;
    private Inselbuch buch;

    @BeforeEach
    void vorher() {
        lager = new SpeicherLager();
        kasse = new SpeicherKasse(1_000_000L, UHR);
        buch = new Inselbuch(Inselgrenze.hafenwerder(), lager, kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
    }

    /** Eine Weltkoordinate mitten auf dem Inselfeld dieser Nummer. */
    private int mitte(int feld) {
        return buch.plan().feldMitte(feld);
    }

    // ===================================================== Nehmen: der Erfolg

    @Test
    @DisplayName("Wer auf einer freien Insel steht, bekommt sie - und zahlt")
    void nehmenGelingt() {
        Inselbuch.Entscheid entscheid = buch.nehmen(WELT, anna, mitte(1), mitte(2), 1);

        assertEquals(Inselbuch.Bescheid.ERTEILT, entscheid.bescheid());
        assertEquals(PREIS, entscheid.kosten());
        Parzelle insel = entscheid.insel().orElseThrow();
        assertEquals(1, insel.x(), "die FELDnummer steht in der Zeile, nicht der Block");
        assertEquals(2, insel.z());
        assertTrue(insel.istWerder());
        assertEquals(anna, insel.eigentuemer());
        assertEquals(1_000_000L - PREIS, kasse.guthaben(anna));
        assertTrue(lager.finde(WELT, 1, 2).isPresent(),
                "und sie liegt in der Ablage - nicht nur im Entscheid");
    }

    @Test
    @DisplayName("Der Preis kommt beim Gebrauch aus dem Stand, nicht vom Bauen")
    void preisKommtAusDemStand() {
        // Darum ist das Feld final und der Wert nicht: Ein /freimarken neuladen muss
        // den naechsten Kauf erreichen, ohne dass das Buch neu gebaut wird - ein neu
        // gebautes Buch waere unsichtbar wirkungslos, weil Befehl und Wache das alte
        // halten.
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Inselbuch eigenes = new Inselbuch(Inselgrenze.hafenwerder(), lager, kasse, stand);
        assertEquals(PREIS, eigenes.preis());

        stand.uebernimm(Wirtschaftswerte.VORGABE.mitInseln(
                Inselwerte.VORGABE.mitPreis(1_000L)));

        assertEquals(1_000L, eigenes.preis());
        assertEquals(1_000L, eigenes.nehmen(WELT, anna, mitte(1), mitte(1), 1).kosten(),
                "und der neue Preis wird auch abgebucht");
        assertEquals(1_000_000L - 1_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Ist der Preis 0, wird nichts gebucht - und die Insel kommt trotzdem")
    void gratisInsel() {
        Inselbuch eigenes = new Inselbuch(Inselgrenze.hafenwerder(), lager, kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitInseln(
                        Inselwerte.VORGABE.mitPreis(0L))));

        Inselbuch.Entscheid entscheid = eigenes.nehmen(WELT, anna, mitte(3), mitte(3), 1);

        assertEquals(Inselbuch.Bescheid.ERTEILT, entscheid.bescheid());
        assertEquals(0L, entscheid.kosten());
        assertEquals(1_000_000L, kasse.guthaben(anna), "kein Pfennig bewegt");
    }

    // ===================================================== Nehmen: die Absagen

    @Test
    @DisplayName("Im offenen Meer gibt es nichts zu nehmen - und die Ablage bleibt stumm")
    void imOffenenMeer() {
        // DIE KOSTENREIHENFOLGE, und sie ist hier beobachtbar gemacht: Die Geometrie
        // entscheidet VOR der Ablage. Mit einem Lager, das beim Fragen wirft, kaeme
        // sonst GESTOERT heraus - und im Spiel haette jeder Klick ins Wasser eine
        // Datenbankabfrage gekostet.
        Inselbuch werfendes = new Inselbuch(Inselgrenze.hafenwerder(), new WerfendesLager(),
                kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        int imMeer = buch.plan().feldAnfang(0) + Inselplan.INSEL_KANTE + 10;

        assertEquals(Inselbuch.Bescheid.KEIN_INSELFELD,
                werfendes.nehmen(WELT, anna, imMeer, imMeer, 1).bescheid());
        assertEquals(1_000_000L, kasse.guthaben(anna), "und kein Geld bewegt");
    }

    @Test
    @DisplayName("Die Ankunftsinsel bleibt frei - und auch dafuer wird nicht gefragt")
    void ankunftsinsel() {
        // VOR dem Nachsehen in der Ablage, und zwar weil die Antwort feststeht: Feld 0/0
        // ist nie vergeben. Ohne diesen Riegel saehe der Spieler "noch frei" - eine
        // Einladung, die nie angenommen werden kann.
        Inselbuch werfendes = new Inselbuch(Inselgrenze.hafenwerder(), new WerfendesLager(),
                kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        assertEquals(Inselbuch.Bescheid.ANKUNFTSINSEL,
                werfendes.nehmen(WELT, anna, mitte(0), mitte(0), 1).bescheid());
        assertTrue(lager.finde(WELT, 0, 0).isEmpty());
    }

    @Test
    @DisplayName("Eine vergebene Insel wird nicht ein zweites Mal verkauft")
    void schonVergeben() {
        buch.nehmen(WELT, anna, mitte(1), mitte(1), 1);
        long bertVorher = kasse.guthaben(bert);

        Inselbuch.Entscheid entscheid = buch.nehmen(WELT, bert, mitte(1), mitte(1), 1);

        assertEquals(Inselbuch.Bescheid.SCHON_VERGEBEN, entscheid.bescheid());
        assertEquals(anna, entscheid.insel().orElseThrow().eigentuemer(),
                "und der Entscheid traegt die Insel - die Absage nennt ihren Besitzer");
        assertEquals(0L, entscheid.kosten());
        assertEquals(bertVorher, kasse.guthaben(bert));
    }

    @Test
    @DisplayName("Jenseits der Weltgrenze gibt es keine Inselfelder")
    void ausserhalb() {
        // Feld 9 gibt es in dieser Welt nicht: 18 Felder je Achse, Nummern -9 bis 8.
        assertEquals(8, buch.grenze().bis());

        Inselbuch.Entscheid entscheid = buch.nehmen(WELT, anna, mitte(9), mitte(0), 1);

        assertEquals(Inselbuch.Bescheid.AUSSERHALB, entscheid.bescheid());
        assertEquals(1_000_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Die Obergrenze zaehlt die Inseln dieser Welt")
    void grenzeErreicht() {
        buch.nehmen(WELT, anna, mitte(1), mitte(1), 1);
        long nachDerErsten = kasse.guthaben(anna);

        Inselbuch.Entscheid zweite = buch.nehmen(WELT, anna, mitte(2), mitte(2), 1);

        assertEquals(Inselbuch.Bescheid.GRENZE_ERREICHT, zweite.bescheid());
        assertEquals(nachDerErsten, kasse.guthaben(anna), "und nichts abgebucht");

        assertEquals(Inselbuch.Bescheid.ERTEILT,
                buch.nehmen(WELT, anna, mitte(2), mitte(2), 2).bescheid(),
                "mit einer hoeheren Grenze geht dieselbe Insel - die Zahl kommt vom"
                        + " Aufrufer und nicht aus dem Buch");
    }

    @Test
    @DisplayName("Eine Hoechstzahl von 0 vergibt gar keine Insel")
    void hoechstzahlNull() {
        // Der Weg, die Inseln abzuschalten, ohne das Recht anzufassen. Ohne eigenen
        // Zweig: 0 >= 0 ist die Grenze, und das ist richtig.
        assertEquals(Inselbuch.Bescheid.GRENZE_ERREICHT,
                buch.nehmen(WELT, anna, mitte(1), mitte(1), 0).bescheid());
    }

    @Test
    @DisplayName("Ohne Geld keine Insel - und der Entscheid nennt den Preis")
    void zuWenigMark() {
        SpeicherKasse arm = new SpeicherKasse(1_000L, UHR);
        Inselbuch eigenes = new Inselbuch(Inselgrenze.hafenwerder(), lager, arm,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        Inselbuch.Entscheid entscheid = eigenes.nehmen(WELT, anna, mitte(1), mitte(1), 1);

        assertEquals(Inselbuch.Bescheid.ZU_WENIG_MARK, entscheid.bescheid());
        assertEquals(PREIS, entscheid.kosten(),
                "der Preis steht im Entscheid - die Absage rechnet daraus, was fehlt");
        assertEquals(1_000L, arm.guthaben(anna));
        assertTrue(lager.finde(WELT, 1, 1).isEmpty(), "und keine Zeile in der Ablage");
    }

    @Test
    @DisplayName("Scheitert das Eintragen, kommt das Geld ZURUECK")
    void gegenbuchungNachDemZahlen() {
        // Nicht theoretisch: Stehen zwei Spieler im selben Augenblick auf derselben
        // Insel und tippen beide, laesst der eindeutige Schluessel den zweiten
        // auflaufen. Ohne die Gegenbuchung zahlte er 75.000 Mark fuer nichts.
        Inselbuch eigenes = new Inselbuch(Inselgrenze.hafenwerder(),
                new LagerDasBeimSchreibenWirft(), kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        Inselbuch.Entscheid entscheid = eigenes.nehmen(WELT, anna, mitte(1), mitte(1), 1);

        assertEquals(Inselbuch.Bescheid.GESTOERT, entscheid.bescheid());
        assertEquals(PREIS, entscheid.kosten(),
                "der Betrag steht im Entscheid, damit ein Protokoll ihn nennen kann");
        assertEquals(1_000_000L, kasse.guthaben(anna), "der Stand ist wieder da");
        assertEquals(0L, kasse.summeSeit(anna, Instant.EPOCH,
                        Buchungsgrund.WERDER_GEKAUFT)
                + kasse.summeSeit(anna, Instant.EPOCH, Buchungsgrund.WERDER_ERSTATTET),
                "und zwar als ZWEI Buchungen, die sich aufheben - nicht als Loch im"
                        + " Buch");
    }

    @Test
    @DisplayName("Eine stumme Ablage heisst GESTOERT und nicht 'frei'")
    void stummeAblage() {
        Inselbuch werfendes = new Inselbuch(Inselgrenze.hafenwerder(), new WerfendesLager(),
                kasse, new Wirtschaftsstand(Wirtschaftswerte.VORGABE));

        assertEquals(Inselbuch.Bescheid.GESTOERT,
                werfendes.nehmen(WELT, anna, mitte(1), mitte(1), 1).bescheid());
        assertEquals(1_000_000L, kasse.guthaben(anna),
                "VOR der Abbuchung - hier ist nichts passiert, auch nicht an der Kasse");
    }

    @Test
    @DisplayName("Das Buch wirft nie nach aussen - auch nicht ohne Grenze")
    void buchWirftNichtNachAussen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Inselbuch(null, lager, kasse,
                        new Wirtschaftsstand(Wirtschaftswerte.VORGABE)),
                "ohne Grenze gibt es keine Welt, in der eine Insel liegen koennte -"
                        + " und das ist ein Bauunfall, kein Betriebsfall");
    }

    // ===================================================== Was hier liegt

    @Test
    @DisplayName("inselAn antwortet im Meer LEER - auch neben einer vergebenen Insel")
    void inselAnImMeer() {
        // DER RIEGEL, DER MEHR IST ALS EINE ABKUERZUNG: feldNummer nennt mitten im Meer
        // das Feld DAVOR. Ohne die Frage nach dem Feld waere die Antwort hier Annas
        // Insel - und die Schutzwache liesse sie ihr Wasser und das halbe Meer dahinter
        // verwalten.
        buch.nehmen(WELT, anna, mitte(0) + 0, mitte(1), 1);
        int imMeer = buch.plan().feldAnfang(1) + Inselplan.INSEL_KANTE + 5;

        assertTrue(buch.inselAn(WELT, imMeer, mitte(1)).isEmpty());
        assertEquals(1, buch.plan().feldNummer(imMeer),
                "und zwar OBWOHL die Feldnummer dort das Feld 1 nennt");
    }

    @Test
    @DisplayName("inselnVon nennt nur die Inseln dieses Spielers")
    void inselnVon() {
        buch.nehmen(WELT, anna, mitte(1), mitte(1), 3);
        buch.nehmen(WELT, anna, mitte(2), mitte(1), 3);
        buch.nehmen(WELT, bert, mitte(3), mitte(1), 3);

        assertEquals(2, buch.inselnVon(WELT, anna).size());
        assertEquals(1, buch.inselnVon(WELT, bert).size());
        assertEquals(2, buch.anzahlVon(WELT, anna));
    }

    // ===================================================== Bauen

    @Test
    @DisplayName("Auf seiner Insel baut der Besitzer - sonst niemand")
    void darfBauen() {
        buch.nehmen(WELT, anna, mitte(1), mitte(1), 1);
        int x = mitte(1);
        int z = mitte(1);

        assertTrue(buch.darfBauen(WELT, anna, x, z));
        assertFalse(buch.darfBauen(WELT, bert, x, z));
        assertFalse(buch.darfBauen(WELT, null, x, z),
                "ohne Spieler ist die Antwort immer nein - die Frage heisst dann"
                        + " 'liegt hier Besitz'");
    }

    @Test
    @DisplayName("Ein Mitbauer baut mit")
    void mitbauerBautMit() {
        // Mitbauer haengen am Datensatz Besitz und gelten fuer JEDE Art. Ohne diese
        // Zusage waere eine Insel der einzige Besitz im Spiel, auf dem niemand
        // mithelfen kann.
        Parzelle insel = buch.nehmen(WELT, anna, mitte(1), mitte(1), 1).insel()
                .orElseThrow();
        lager.mitbauerSetzen(insel.id(), Set.of(bert));

        assertTrue(buch.darfBauen(WELT, bert, mitte(1), mitte(1)));
    }

    @Test
    @DisplayName("Im offenen Meer baut NIEMAND - auch der Nachbar nicht")
    void imMeerBautNiemand() {
        // Erst damit stimmt, was der Plan ueber die 128 Bloecke Meer sagt: Es ist das
        // oeffentliche Wasser des Archipels. Haette der Nachbar dort Recht, waere die
        // Haelfte des Meeres vergeben, ohne dass jemand dafuer gezahlt hat.
        buch.nehmen(WELT, anna, mitte(1), mitte(1), 1);
        int imMeer = buch.plan().feldAnfang(1) + Inselplan.INSEL_KANTE + 5;

        assertFalse(buch.darfBauen(WELT, anna, imMeer, mitte(1)));
        assertFalse(buch.darfBauen(WELT, bert, imMeer, mitte(1)));
    }

    @Test
    @DisplayName("Auf einer freien Insel und auf der Ankunftsinsel baut niemand")
    void aufFreiemFeldBautNiemand() {
        // Die Ankunftsinsel braucht KEINEN eigenen Zweig, und das ist die Zusage dieser
        // Probe: Sie ist nie vergeben, also faellt sie durch denselben Weg wie jedes
        // freie Feld. Ein eigener Zweig daneben waere ein zweiter Ort fuer dieselbe
        // Antwort.
        assertFalse(buch.darfBauen(WELT, anna, mitte(4), mitte(4)));
        assertFalse(buch.darfBauen(WELT, anna, buch.plan().ankunftsMitte(),
                buch.plan().ankunftsMitte()));
    }

    // ===================================================== Die Stellvertreter

    /**
     * Ein Lager, das bei JEDER Frage wirft.
     *
     * <p>Damit ist zu sehen, WANN das Buch die Ablage ueberhaupt anfasst - und das ist
     * die Zusage der Kostenreihenfolge: erst rechnen, dann nachsehen, zuletzt Geld.
     */
    private static final class WerfendesLager extends Lagerattrappe {

        @Override
        public java.util.Optional<Parzelle> finde(String welt, int x, int z) {
            throw new IllegalStateException("Die Datenbank antwortet nicht");
        }

        @Override
        public int anzahlVon(String welt, UUID spieler) {
            throw new IllegalStateException("Die Datenbank antwortet nicht");
        }
    }

    /** Ein Lager, das erst beim SCHREIBEN wirft - nach der Abbuchung. */
    private static final class LagerDasBeimSchreibenWirft extends Lagerattrappe {

        @Override
        public Parzelle werderAnlegen(String welt, int feldX, int feldZ,
                                      UUID eigentuemer, long preis) {
            throw new IllegalStateException("Das Feld ist bereits vergeben");
        }
    }
}
