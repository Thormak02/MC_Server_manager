package de.freimarken.kern;

import static de.freimarken.kern.Freistadtbuch.Bescheid.GEGRUENDET;
import static de.freimarken.kern.Freistadtbuch.Bescheid.GRENZE_ERREICHT;
import static de.freimarken.kern.Freistadtbuch.Bescheid.KEIN_FREIES_FELD;
import static de.freimarken.kern.Freistadtbuch.Bescheid.NAME_FALSCH;
import static de.freimarken.kern.Freistadtbuch.Bescheid.NAME_RESERVIERT;
import static de.freimarken.kern.Freistadtbuch.Bescheid.NAME_VERGEBEN;
import static de.freimarken.kern.Freistadtbuch.Bescheid.NAME_ZU_LANG;
import static de.freimarken.kern.Freistadtbuch.Bescheid.ZU_WENIG_MARK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Spielregeln rund um das Gruenden einer Freistadt - ohne Server, ohne Datenbank.
 *
 * <p>Die Zahlen sind die AUSGELIEFERTEN: 400.000 Mark, eine Stadt je Spieler. Dass die
 * Regeln auch mit anderen Zahlen stimmen, pruefen die Proben, die sie ausdruecklich
 * umstellen - und die stellen sie selbst um, statt sich auf die Vorgabe zu verlassen.
 */
class FreistadtbuchTest {

    private static final String WELT = "freimarken:freistaedte";
    private static final long REICH = 10_000_000L;

    /** Eine Stadt kostet so viel, dass ein Startguthaben nicht reicht. */
    private static final long PREIS = Freistadtwerte.VORGABE_GRUENDUNGSPREIS;

    /** Hoechstzahl fuer Proben, die an der Zahl nicht haengen. */
    private static final int VIELE = 99;

    private final UUID anna = UUID.randomUUID();
    private final UUID bert = UUID.randomUUID();

    private static final Wirtschaftsstand VORGABE =
            new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

    /**
     * Die Welten, die es laut Einstellungsdatei gibt - und damit die reservierten Namen.
     *
     * <p>Ausdruecklich mit {@code kornmark} und {@code freistaedte} besetzt: Genau diese
     * zwei sind die Faelle, in denen ein Stadtname einen Warp mehrdeutig machte.
     */
    private static final Warpverzeichnis WARPS =
            Warpverzeichnis.ausWelten(List.of("kornmark", "altfurt", "freistaedte"));

    private SpeicherLager lager;
    private Kasse kasse;
    private Freistadtbuch buch;

    @BeforeEach
    void aufbauen() {
        lager = new SpeicherLager();
        kasse = new SpeicherKasse(REICH);
        buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse, WARPS, VORGABE);
    }

    @Test
    @DisplayName("Gegruendet: die Stadt steht, das Geld ist weg, das Feld ist zugeteilt")
    void gruendenGeht() {
        Freistadtbuch.Entscheid entscheid = buch.gruende(WELT, anna, "neustadt", VIELE);

        assertEquals(GEGRUENDET, entscheid.bescheid());
        assertTrue(entscheid.erfolgreich());
        assertEquals(PREIS, entscheid.kosten());
        assertEquals(REICH - PREIS, kasse.guthaben(anna));

        Parzelle stadt = entscheid.stadt().orElseThrow();
        assertEquals("neustadt", stadt.name());
        assertEquals(anna, stadt.eigentuemer());
        assertEquals(Art.FREISTADT, stadt.art());
        assertTrue(buch.grenze().enthaeltFeld(stadt.x(), stadt.z()),
                "das zugeteilte Feld muss INNERHALB der Weltgrenze liegen - sonst"
                        + " kaeme der Besitzer nie hinein");
    }

    @Test
    @DisplayName("Das Ankunftsfeld wird nie vergeben - dort landet das Tor aus Altfurt")
    void ankunftsfeldBleibtFrei() {
        // Zugeteilt wird von innen nach aussen, also waere 0/0 das erste Feld - genau
        // darum muss diese Probe als erste gruendende dastehen.
        Parzelle erste = buch.gruende(WELT, anna, "erste", VIELE).stadt().orElseThrow();

        assertNotEquals(0, Math.abs(erste.x()) + Math.abs(erste.z()),
                "0/0 ist das Ankunftsfeld und gehoert niemandem");
        assertTrue(buch.plan().istAnkunftsfeld(0, 0));
    }

    @Test
    @DisplayName("Jede Stadt bekommt ihr eigenes Feld")
    void jedeStadtEigenesFeld() {
        Parzelle eine = buch.gruende(WELT, anna, "aue", VIELE).stadt().orElseThrow();
        Parzelle andere = buch.gruende(WELT, bert, "moor", VIELE).stadt().orElseThrow();

        assertNotEquals(eine.x() + "/" + eine.z(), andere.x() + "/" + andere.z());
        assertEquals(2, buch.staedte(WELT).size());
    }

    @Test
    @DisplayName("Derselbe Name geht nicht zweimal - und kostet den Zweiten nichts")
    void nameNurEinmal() {
        buch.gruende(WELT, anna, "doppelt", VIELE);
        long vorher = kasse.guthaben(bert);

        Freistadtbuch.Entscheid zweiter = buch.gruende(WELT, bert, "doppelt", VIELE);

        assertEquals(NAME_VERGEBEN, zweiter.bescheid());
        assertEquals(vorher, kasse.guthaben(bert), "bei einer Absage ist nichts passiert -"
                + " auch nicht an der Kasse");
        assertEquals(1, buch.staedte(WELT).size());
    }

    @Test
    @DisplayName("Gross und klein ist derselbe Name")
    void grossKleinIstDieselbeStadt() {
        buch.gruende(WELT, anna, "neustadt", VIELE);

        assertEquals(NAME_VERGEBEN, buch.gruende(WELT, bert, "NeuStadt", VIELE).bescheid());
    }

    @Test
    @DisplayName("Ein Name, der einer Welt gehoert, macht den Warp mehrdeutig")
    void weltnamenSindReserviert() {
        assertEquals(NAME_RESERVIERT, buch.gruende(WELT, anna, "kornmark", VIELE).bescheid());
        assertEquals(NAME_RESERVIERT, buch.gruende(WELT, anna, "Altfurt", VIELE).bescheid());
        assertEquals(REICH, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Die Form des Namens wird vor jedem Blick in die Ablage geprueft")
    void namensformZuerst() {
        // Zwei verschiedene Absagen und kein Sammeltopf: Die Laenge nennt eine Zahl, die
        // Zeichen nennen die erlaubten. Wer beides als NAME_FALSCH meldete, liesse den
        // Spieler raten, welches von beidem gemeint ist.
        assertEquals(NAME_ZU_LANG, buch.gruende(WELT, anna, "ab", VIELE).bescheid());
        assertEquals(NAME_ZU_LANG, buch.gruende(WELT, anna, "  ", VIELE).bescheid());
        assertEquals(NAME_ZU_LANG,
                buch.gruende(WELT, anna, "a".repeat(33), VIELE).bescheid());

        assertEquals(NAME_FALSCH, buch.gruende(WELT, anna, "Neu Stadt", VIELE).bescheid());
        assertEquals(NAME_FALSCH, buch.gruende(WELT, anna, "neu_stadt", VIELE).bescheid());
        assertEquals(NAME_FALSCH, buch.gruende(WELT, anna, "neu.stadt", VIELE).bescheid());
        assertEquals(NAME_FALSCH, buch.gruende(WELT, anna, "1stadt", VIELE).bescheid());

        assertEquals(REICH, kasse.guthaben(anna), "keine dieser Absagen kostet etwas");
        assertTrue(buch.staedte(WELT).isEmpty());
    }

    @Test
    @DisplayName("Die Hoechstzahl zaehlt die eigenen Staedte, nicht die fremden")
    void hoechstzahlZaehltEigene() {
        assertEquals(GEGRUENDET, buch.gruende(WELT, anna, "annas-stadt", 1).bescheid());
        assertEquals(GRENZE_ERREICHT, buch.gruende(WELT, anna, "noch-eine", 1).bescheid());

        // Berts erste Stadt geht trotzdem - annas Stadt zaehlt fuer ihn nicht.
        assertEquals(GEGRUENDET, buch.gruende(WELT, bert, "berts-stadt", 1).bescheid());
        assertEquals(REICH - PREIS, kasse.guthaben(anna),
                "die abgelehnte zweite Stadt hat nichts gekostet");
    }

    @Test
    @DisplayName("Mit zwei erlaubten Staedten gruendet derselbe Spieler zweimal")
    void zweiStaedteSindMoeglich() {
        // Die Hoechstzahl ist eine ZAHL und keine Eins im Programm: Schritt 12 haengt die
        // Raenge daran. Diese Probe ist der Beleg, dass die Regel wirklich zaehlt.
        assertEquals(GEGRUENDET, buch.gruende(WELT, anna, "erste", 2).bescheid());
        assertEquals(GEGRUENDET, buch.gruende(WELT, anna, "zweite", 2).bescheid());
        assertEquals(GRENZE_ERREICHT, buch.gruende(WELT, anna, "dritte", 2).bescheid());
        assertEquals(REICH - 2 * PREIS, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Ohne Geld keine Stadt - und die Absage nennt den Preis")
    void ohneGeldKeineStadt() {
        Kasse arm = new SpeicherKasse(PREIS - 1L);
        Freistadtbuch armesBuch =
                new Freistadtbuch(Feldgrenze.freistaedte(), lager, arm, WARPS, VORGABE);

        Freistadtbuch.Entscheid entscheid = armesBuch.gruende(WELT, anna, "traum", VIELE);

        assertEquals(ZU_WENIG_MARK, entscheid.bescheid());
        assertEquals(PREIS, entscheid.kosten(), "der Spieler soll lesen, was ihm fehlt");
        assertEquals(PREIS - 1L, arm.guthaben(anna), "und sein Guthaben ist unberuehrt");
        assertTrue(armesBuch.staedte(WELT).isEmpty());
    }

    @Test
    @DisplayName("Ein geaenderter Preis wirkt sofort - er wird beim Buchen geholt")
    void preisKommtAusDemStand() {
        // Der Wirtschaftsstand ist veraenderlich, das Buch nicht: Ein beim Neuladen neu
        // gebautes Freistadtbuch waere unsichtbar wirkungslos, weil die Befehle noch auf
        // das alte zeigen. Darum wird der Preis beim GEBRAUCH geholt.
        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);
        Freistadtbuch eigenes =
                new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse, WARPS, stand);
        assertEquals(PREIS, eigenes.preis());

        stand.uebernimm(Wirtschaftswerte.VORGABE.mitFreistaedten(
                Freistadtwerte.VORGABE.mitGruendungspreis(7_000L)));

        assertEquals(7_000L, eigenes.preis());
        assertEquals(7_000L, eigenes.gruende(WELT, anna, "billig", VIELE).kosten());
        assertEquals(REICH - 7_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Ist die Welt voll, sagt das Buch es - statt ein belegtes Feld zu vergeben")
    void volleWeltSagtAb() {
        // Eine winzige Welt: zwei Felder je Achse, also vier, davon eines das
        // Ankunftsfeld. Drei Staedte passen hinein, die vierte nicht mehr.
        Feldgrenze winzig = Feldgrenze.ausKante(Stadtfeldplan.freistaedte(), 608);
        assertEquals(2, winzig.felderJeAchse());
        Freistadtbuch enges =
                new Freistadtbuch(winzig, lager, kasse, WARPS, VORGABE);

        assertEquals(GEGRUENDET, enges.gruende(WELT, anna, "eins", VIELE).bescheid());
        assertEquals(GEGRUENDET, enges.gruende(WELT, anna, "zwei", VIELE).bescheid());
        assertEquals(GEGRUENDET, enges.gruende(WELT, anna, "drei", VIELE).bescheid());

        Freistadtbuch.Entscheid voll = enges.gruende(WELT, anna, "vier", VIELE);
        assertEquals(KEIN_FREIES_FELD, voll.bescheid());
        assertEquals(REICH - 3 * PREIS, kasse.guthaben(anna),
                "die abgelehnte vierte Stadt hat nichts gekostet");

        // Und kein Feld doppelt: drei Staedte, drei verschiedene Felder.
        assertEquals(3, enges.staedte(WELT).stream()
                .map(stadt -> stadt.x() + "/" + stadt.z())
                .distinct().count());
    }

    @Test
    @DisplayName("Die Staedte einer anderen Welt zaehlen hier nicht mit")
    void weltenSindGetrennt() {
        buch.gruende("freimarken:andere", anna, "drueben", 1);

        assertEquals(GEGRUENDET, buch.gruende(WELT, anna, "hier", 1).bescheid(),
                "die Hoechstzahl gilt je Welt - drueben ist eine andere Welt");
        assertEquals(1, buch.staedte(WELT).size());
    }

    @Test
    @DisplayName("staedteVon zaehlt nur die eigenen")
    void staedteVonZaehltEigene() {
        buch.gruende(WELT, anna, "annas-eine", VIELE);
        buch.gruende(WELT, anna, "annas-zwei", VIELE);
        buch.gruende(WELT, bert, "berts-eine", VIELE);

        assertEquals(2, buch.staedteVon(WELT, anna));
        assertEquals(1, buch.staedteVon(WELT, bert));
        assertEquals(0, buch.staedteVon(WELT, UUID.randomUUID()));
    }

    // ------------------------------------- Das Grundstueck sticht die Stadt (C2)

    @Test
    @DisplayName("Ein vergebenes Grundstueck sticht die Stadt - AUCH beim Stadtbesitzer")
    void grundstueckStichtDieStadt() {
        // DIE TRAGENDE ZUSAGE VON TEIL C2. Ohne sie waere ein verkauftes Grundstueck
        // eine Zeile in der Datenbank ohne jede Wirkung, und der Kaeufer haette fuer
        // eine Flaeche gezahlt, auf der der Verkaeufer weiterbaut.
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        Stadtfeldplan plan = buch.plan();
        int zelleX = plan.ersteZelle(stadt.x());
        int zelleZ = plan.ersteZelle(stadt.z());
        int blockX = plan.zellenAnfang(zelleX) + 3;
        int blockZ = plan.zellenAnfang(zelleZ) + 3;

        // Vorher: Annas Feld, Anna darf.
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT,
                buch.darfBauen(WELT, anna, blockX, blockZ));

        // Das Grundstueck gehoert BERT - so, wie es nach einem Verkauf (Teil C3) sein
        // wird.
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(zelleX, zelleZ),
                bert, 5_000L);

        assertEquals(Freistadtbuch.Bauurteil.FREMDES_GRUNDSTUECK,
                buch.darfBauen(WELT, anna, blockX, blockZ),
                "die Stadtbesitzerin darf auf dem vergebenen Grundstueck NICHT mehr");
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_GRUNDSTUECK,
                buch.darfBauen(WELT, bert, blockX, blockZ),
                "der Grundstuecksbesitzer darf");
        // Und EINE Zelle weiter gilt wieder die Stadt.
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT,
                buch.darfBauen(WELT, anna, plan.zellenAnfang(zelleX + 1) + 3, blockZ),
                "daneben ist es weiter Annas Feld");
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, bert, plan.zellenAnfang(zelleX + 1) + 3, blockZ),
                "und dort darf Bert nicht");
    }

    @Test
    @DisplayName("Ein MITBAUER des Grundstuecks darf - der der Stadt nicht")
    void mitbauerDesGrundstuecks() {
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        Stadtfeldplan plan = buch.plan();
        int zelleX = plan.ersteZelle(stadt.x());
        int zelleZ = plan.ersteZelle(stadt.z());
        UUID clara = UUID.randomUUID();
        Grundstueck bertsLand = grundstuecke.anlegen(WELT, stadt.id(),
                Zellquader.eineZelle(zelleX, zelleZ), bert, 5_000L);
        // Clara ist Mitbauerin DER STADT - das zaehlt auf dem Grundstueck nicht.
        lager.mitbauerSetzen(stadt.id(), java.util.Set.of(clara));
        int blockX = plan.zellenAnfang(zelleX) + 3;
        int blockZ = plan.zellenAnfang(zelleZ) + 3;

        assertEquals(Freistadtbuch.Bauurteil.FREMDES_GRUNDSTUECK,
                buch.darfBauen(WELT, clara, blockX, blockZ),
                "Mitbauerin der STADT zu sein hilft auf einem vergebenen Grundstueck"
                        + " nicht - sonst koennte ein Stadtbesitzer sich selbst oder"
                        + " jeden Freund auf fremdes Land setzen");

        grundstuecke.mitbauerSetzen(bertsLand.id(), java.util.Set.of(clara));

        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_GRUNDSTUECK,
                buch.darfBauen(WELT, clara, blockX, blockZ),
                "als Mitbauerin DES GRUNDSTUECKS darf sie");
    }

    @Test
    @DisplayName("Ohne Grundstuecksablage urteilt das Buch wie in Teil C1")
    void ohneAblageWieC1() {
        // Die Zusage fuer den Start: Faellt die Grundstuecksablage aus, soll der Server
        // weiter Staedte schuetzen koennen. Weniger fein, aber er schuetzt.
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);

        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT, buch.darfBauen(WELT, anna,
                buch.plan().feldMitte(stadt.x()), buch.plan().feldMitte(stadt.z())));
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT, buch.darfBauen(WELT, bert,
                buch.plan().feldMitte(stadt.x()), buch.plan().feldMitte(stadt.z())));
    }

    @Test
    @DisplayName("Die LEERE wird auch MIT Grundstuecken nicht nach ihnen gefragt")
    void leereFragtAuchJetztNicht() {
        // Die Reihenfolge in darfBauen: Leere, Grenze, Ankunftsfeld, dann Stadt, dann
        // Grundstueck. Ein Grundstueck in der Leere kann es nicht geben - also darf die
        // Leere auch nicht danach fragen. Gezaehlt, nicht behauptet.
        ZaehlendeGrundstuecke zaehler = new ZaehlendeGrundstuecke();
        buch.gruende(WELT, anna, "annastadt", VIELE);
        buch.gibGrundstuecke(zaehler);
        Parzelle stadt = buch.staedte(WELT).get(0);
        Stadtfeldplan plan = buch.plan();

        buch.darfBauen(WELT, anna,
                plan.feldAnfang(stadt.x()) + Stadtfeldplan.FELD_KANTE,
                plan.feldMitte(stadt.z()));
        assertEquals(0, zaehler.gefragt, "die Leere fragt die Grundstuecke nicht");

        buch.darfBauen(WELT, anna, plan.feldMitte(stadt.x()), plan.feldMitte(stadt.z()));
        assertEquals(1, zaehler.gefragt, "auf dem Feld genau einmal");
    }

    /** Eine Grundstuecksablage, die mitzaehlt, wie oft nach einer Zelle gefragt wurde. */
    private static final class ZaehlendeGrundstuecke implements Grundstuecksablage {
        private final SpeicherGrundstuecke echte = new SpeicherGrundstuecke();
        private int gefragt;

        @Override
        public java.util.Optional<Grundstueck> anZelle(String welt, int zelleX,
                                                       int zelleZ) {
            gefragt++;
            return echte.anZelle(welt, zelleX, zelleZ);
        }

        @Override
        public Grundstueck anlegen(String welt, long stadtId, Zellquader flaeche,
                                   UUID eigentuemer, long preis) {
            return echte.anlegen(welt, stadtId, flaeche, eigentuemer, preis);
        }

        @Override
        public List<Grundstueck> inStadt(long stadtId) {
            return echte.inStadt(stadtId);
        }

        @Override
        public List<Grundstueck> alle(String welt) {
            return echte.alle(welt);
        }

        @Override
        public List<Grundstueck> vonSpieler(String welt, UUID spieler) {
            return echte.vonSpieler(welt, spieler);
        }

        @Override
        public void mitbauerSetzen(long id, java.util.Set<UUID> mitbauer) {
            echte.mitbauerSetzen(id, mitbauer);
        }

        @Override
        public void entfernen(long id) {
            echte.entfernen(id);
        }
    }

    // ---------------------------------------------------- Wer darf wo bauen (C1)

    /**
     * Die sechs Urteile der Bauregel - jedes an seiner eigenen Stelle im Gelaende.
     *
     * <p>Gerechnet wird mit dem ausgelieferten Mass: Felder von 256 Bloecken, dazwischen
     * 32 Bloecke Leere, Feld n beginnt bei {@code feldAnfang(n)}. Die Zahlen stehen hier
     * NICHT als Literale, sondern kommen aus dem Plan - sonst braeche jede Aenderung an
     * der Feldkante ein halbes Dutzend Proben, die mit der Kante gar nichts zu tun haben.
     */
    @Test
    @DisplayName("Jede Lage der Stadtfelderwelt hat ihr eigenes Urteil")
    void jedeLageHatIhrUrteil() {
        Stadtfeldplan plan = buch.plan();
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);
        int innen = plan.feldMitte(stadt.x());
        int innenZ = plan.feldMitte(stadt.z());

        // 1. Die Eigentuemerin mitten in ihrer Stadt.
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT,
                buch.darfBauen(WELT, anna, innen, innenZ));
        // 2. Ein anderer an derselben Stelle.
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, bert, innen, innenZ));
        // 3. Die LEERE - einen Block hinter der Feldkante. Fuer JEDEN verboten, auch
        //    fuer die Eigentuemerin des Feldes daneben: Ein Block dort ist der erste
        //    Block einer Bruecke.
        int hinterDemFeld = plan.feldAnfang(stadt.x()) + Stadtfeldplan.FELD_KANTE;
        assertEquals(Freistadtbuch.Bauurteil.LEERE,
                buch.darfBauen(WELT, anna, hinterDemFeld, innenZ));
        // 4. Das Ankunftsfeld - gehoert niemandem und wird nie vergeben.
        assertEquals(Freistadtbuch.Bauurteil.ANKUNFTSFELD,
                buch.darfBauen(WELT, anna, plan.feldMitte(0), plan.feldMitte(0)));
        // 5. Ein freies Stadtfeld.
        int freiesFeld = stadt.x() == 1 ? 2 : 1;
        assertEquals(Freistadtbuch.Bauurteil.FREIES_FELD,
                buch.darfBauen(WELT, anna, plan.feldMitte(freiesFeld), innenZ));
        // 6. Jenseits der Weltgrenze.
        assertEquals(Freistadtbuch.Bauurteil.AUSSERHALB,
                buch.darfBauen(WELT, anna,
                        plan.feldMitte(buch.grenze().bis() + 1), innenZ));
    }

    @Test
    @DisplayName("Ein Mitbauer der Stadt darf, ein Fremder nicht")
    void mitbauerDarf() {
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);
        int x = buch.plan().feldMitte(stadt.x());
        int z = buch.plan().feldMitte(stadt.z());
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, bert, x, z), "vorher");

        lager.mitbauerSetzen(stadt.id(), java.util.Set.of(bert));

        // Die Mitbauer kommen aus DERSELBEN Zeile wie der Eigentuemer - die Regel fragt
        // Parzelle.darfBauen und nicht noch einmal selbst nach. Ein zweiter Ort fuer
        // "wer darf hier" liefe irgendwann auseinander.
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT,
                buch.darfBauen(WELT, bert, x, z));
    }

    @Test
    @DisplayName("Ohne Spieler wird nie erlaubt - auch nicht in einer freien Stadt")
    void ohneSpielerNieErlaubt() {
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);

        // Der Handbalken fragt OHNE Spieler, weil er nicht wissen will, ob jemand darf,
        // sondern WAS hier liegt. Dann muss FREMDE_STADT herauskommen und nicht ERLAUBT -
        // sonst bekaeme der Eigentuemer seinen eigenen Balken nie zu sehen, und ein
        // Programmierfehler in der Wache waere unsichtbar.
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, null, buch.plan().feldMitte(stadt.x()),
                        buch.plan().feldMitte(stadt.z())));
    }

    @Test
    @DisplayName("Leere, Grenze und Ankunftsfeld fragen die Ablage GAR NICHT")
    void dreiLagenOhneAblage() {
        // GEZAEHLT und nicht behauptet. Diese drei Lagen sind die haeufigsten am
        // Feldrand, und die Regel laeuft bei JEDEM gesetzten Block - eine Abfrage dort
        // waere ein Gang zur Datenbank je Blockereignis. Die Zahl 0 ist die ganze
        // Zusage; ohne sie koennte die Reihenfolge in darfBauen still umgestellt
        // werden, und nichts wuerde rot.
        Lagerattrappe zaehler = new Lagerattrappe();
        Freistadtbuch gezaehlt = new Freistadtbuch(Feldgrenze.freistaedte(), zaehler,
                kasse, WARPS, VORGABE);
        Stadtfeldplan plan = gezaehlt.plan();

        assertEquals(Freistadtbuch.Bauurteil.LEERE, gezaehlt.darfBauen(WELT, anna,
                plan.feldAnfang(1) + Stadtfeldplan.FELD_KANTE, plan.feldMitte(1)));
        assertEquals(Freistadtbuch.Bauurteil.AUSSERHALB, gezaehlt.darfBauen(WELT, anna,
                plan.feldMitte(gezaehlt.grenze().bis() + 1), plan.feldMitte(1)));
        assertEquals(Freistadtbuch.Bauurteil.ANKUNFTSFELD, gezaehlt.darfBauen(WELT, anna,
                plan.feldMitte(0), plan.feldMitte(0)));
        assertEquals(0, zaehler.gezaehlt(),
                "keine dieser drei Lagen darf die Ablage anfassen");

        // Und auf einem Stadtfeld GENAU EINMAL - dort ist die Frage unvermeidlich, aber
        // eine zweite waere ein doppelter Gang auf dem heissen Pfad.
        gezaehlt.darfBauen(WELT, anna, plan.feldMitte(1), plan.feldMitte(1));
        assertEquals(1, zaehler.gezaehlt());
    }

    // ------------------------------------------- Das Verzeichnis fuer die Vorschlaege

    @Test
    @DisplayName("Eine Gruendung steht sofort im Verzeichnis - ohne Neustart")
    void gruendenFuelltDasVerzeichnis() {
        assertEquals(0, buch.verzeichnis().anzahl(), "ab Werk kennt es nichts");

        buch.gruende(WELT, anna, "Neustadt", VIELE);

        // Klein, wie in der Ablage - und sofort: Der Besitzer tippt als Ersten den Namen
        // seiner eigenen, frisch gegruendeten Stadt. Stuende sie erst nach dem naechsten
        // Neustart im Fenster, waere das Verzeichnis genau dann blind, wenn es gebraucht
        // wird.
        assertEquals(List.of("neustadt"), buch.verzeichnis().vorschlaege(""));
    }

    @Test
    @DisplayName("Eine ABGELEHNTE Gruendung kommt NICHT ins Verzeichnis")
    void abgelehnteGruendungFuelltNichts() {
        kasse = new SpeicherKasse(PREIS - 1L);
        buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse, WARPS, VORGABE);

        assertEquals(ZU_WENIG_MARK, buch.gruende(WELT, anna, "neustadt", VIELE)
                .bescheid());

        // Sonst schluege der Server eine Reise in eine Stadt vor, die es nicht gibt -
        // und sie endete in der Absage "Die Freistadt neustadt gibt es nicht."
        assertEquals(0, buch.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("erneuereVerzeichnis holt, was schon in der Ablage steht")
    void erneuernHoltAusDerAblage() {
        // Am Buch VORBEI angelegt - so sieht es nach einem Neustart aus: Die Staedte
        // stehen in der Datenbank, und das Verzeichnis im Arbeitsspeicher ist leer.
        lager.freistadtAnlegen(WELT, 3, 4, anna, PREIS, "altdorf");
        lager.freistadtAnlegen(WELT, 5, 6, bert, PREIS, "neustadt");
        assertEquals(0, buch.verzeichnis().anzahl(),
                "ohne den Aufruf weiss das Verzeichnis von keiner einzigen");

        assertEquals(2, buch.erneuereVerzeichnis(WELT),
                "die Zahl geht in die Startzeile - daran haengt der Aufruf");

        assertEquals(List.of("altdorf", "neustadt"), buch.verzeichnis().vorschlaege(""));
    }

    @Test
    @DisplayName("erneuereVerzeichnis nimmt nur SEINE Welt")
    void erneuernNimmtNurSeineWelt() {
        lager.freistadtAnlegen(WELT, 3, 4, anna, PREIS, "hier");
        lager.freistadtAnlegen("freimarken:andere", 3, 4, bert, PREIS, "drueben");

        assertEquals(1, buch.erneuereVerzeichnis(WELT));

        assertEquals(List.of("hier"), buch.verzeichnis().vorschlaege(""),
                "eine Stadt aus einer anderen Welt waere ein Vorschlag, der in dieser"
                        + " Welt ins Leere fuehrt");
    }
}
