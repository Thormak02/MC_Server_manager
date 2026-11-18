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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Set;
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

    /** Die Kasse der STAEDTE - ohne Startguthaben [Teil E1]. */
    private SpeicherKasse staedtekasse;

    private Stadtkasse stadtkasse;
    private Freistadtbuch buch;

    @BeforeEach
    void aufbauen() {
        lager = new SpeicherLager();
        kasse = new SpeicherKasse(REICH);
        staedtekasse = new SpeicherKasse(0L);
        stadtkasse = new Stadtkasse(staedtekasse);
        buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse, stadtkasse,
                WARPS, VORGABE);
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
                new Freistadtbuch(Feldgrenze.freistaedte(), lager, arm,
                        stadtkasse, WARPS, VORGABE);

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
                new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                        stadtkasse, WARPS, stand);
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
                new Freistadtbuch(winzig, lager, kasse, stadtkasse, WARPS,
                        VORGABE);

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
                buch.darfBauen(WELT, anna, Tat.BAUEN, blockX, blockZ));

        // Das Grundstueck gehoert BERT - so, wie es nach einem Verkauf (Teil C3) sein
        // wird.
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(zelleX, zelleZ),
                bert, 5_000L);

        assertEquals(Freistadtbuch.Bauurteil.FREMDES_GRUNDSTUECK,
                buch.darfBauen(WELT, anna, Tat.BAUEN, blockX, blockZ),
                "die Stadtbesitzerin darf auf dem vergebenen Grundstueck NICHT mehr");
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_GRUNDSTUECK,
                buch.darfBauen(WELT, bert, Tat.BAUEN, blockX, blockZ),
                "der Grundstuecksbesitzer darf");
        // Und EINE Zelle weiter gilt wieder die Stadt.
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT,
                buch.darfBauen(WELT, anna, Tat.BAUEN,
                        plan.zellenAnfang(zelleX + 1) + 3, blockZ),
                "daneben ist es weiter Annas Feld");
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, bert, Tat.BAUEN,
                        plan.zellenAnfang(zelleX + 1) + 3, blockZ),
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
                buch.darfBauen(WELT, clara, Tat.BAUEN, blockX, blockZ),
                "Mitbauerin der STADT zu sein hilft auf einem vergebenen Grundstueck"
                        + " nicht - sonst koennte ein Stadtbesitzer sich selbst oder"
                        + " jeden Freund auf fremdes Land setzen");

        grundstuecke.mitbauerSetzen(bertsLand.id(), java.util.Set.of(clara));

        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_GRUNDSTUECK,
                buch.darfBauen(WELT, clara, Tat.BAUEN, blockX, blockZ),
                "als Mitbauerin DES GRUNDSTUECKS darf sie");
    }

    @Test
    @DisplayName("Ohne Grundstuecksablage urteilt das Buch wie in Teil C1")
    void ohneAblageWieC1() {
        // Die Zusage fuer den Start: Faellt die Grundstuecksablage aus, soll der Server
        // weiter Staedte schuetzen koennen. Weniger fein, aber er schuetzt.
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);

        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT, buch.darfBauen(WELT, anna, Tat.BAUEN,
                buch.plan().feldMitte(stadt.x()), buch.plan().feldMitte(stadt.z())));
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT, buch.darfBauen(WELT, bert, Tat.BAUEN,
                buch.plan().feldMitte(stadt.x()), buch.plan().feldMitte(stadt.z())));
    }

    @Test
    @DisplayName("Die LEERE wird auch MIT Grundstuecken nicht nach ihnen gefragt")
    void leereFragtAuchJetztNicht() {
        // Die Reihenfolge in darfBauen: Leere, Grenze, Ankunftsfeld, dann Stadt, dann
        // Grundstueck. Ein Grundstueck in der Leere kann es nicht geben - also darf die
        // Leere auch nicht danach fragen. Gezaehlt, nicht behauptet.
        Grundstuecksattrappe zaehler = new Grundstuecksattrappe();
        buch.gruende(WELT, anna, "annastadt", VIELE);
        buch.gibGrundstuecke(zaehler);
        Parzelle stadt = buch.staedte(WELT).get(0);
        Stadtfeldplan plan = buch.plan();

        buch.darfBauen(WELT, anna, Tat.BAUEN,
                plan.feldAnfang(stadt.x()) + Stadtfeldplan.FELD_KANTE,
                plan.feldMitte(stadt.z()));
        assertEquals(0, zaehler.gezaehlt(), "die Leere fragt die Grundstuecke nicht");

        buch.darfBauen(WELT, anna, Tat.BAUEN, plan.feldMitte(stadt.x()),
                plan.feldMitte(stadt.z()));
        assertEquals(1, zaehler.gezaehlt(), "auf dem Feld genau einmal");
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
                buch.darfBauen(WELT, anna, Tat.BAUEN, innen, innenZ));
        // 2. Ein anderer an derselben Stelle.
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, bert, Tat.BAUEN, innen, innenZ));
        // 3. Die LEERE - einen Block hinter der Feldkante. Fuer JEDEN verboten, auch
        //    fuer die Eigentuemerin des Feldes daneben: Ein Block dort ist der erste
        //    Block einer Bruecke.
        int hinterDemFeld = plan.feldAnfang(stadt.x()) + Stadtfeldplan.FELD_KANTE;
        assertEquals(Freistadtbuch.Bauurteil.LEERE,
                buch.darfBauen(WELT, anna, Tat.BAUEN, hinterDemFeld, innenZ));
        // 4. Das Ankunftsfeld - gehoert niemandem und wird nie vergeben.
        assertEquals(Freistadtbuch.Bauurteil.ANKUNFTSFELD,
                buch.darfBauen(WELT, anna, Tat.BAUEN, plan.feldMitte(0), plan.feldMitte(0)));
        // 5. Ein freies Stadtfeld.
        int freiesFeld = stadt.x() == 1 ? 2 : 1;
        assertEquals(Freistadtbuch.Bauurteil.FREIES_FELD,
                buch.darfBauen(WELT, anna, Tat.BAUEN, plan.feldMitte(freiesFeld), innenZ));
        // 6. Jenseits der Weltgrenze.
        assertEquals(Freistadtbuch.Bauurteil.AUSSERHALB,
                buch.darfBauen(WELT, anna, Tat.BAUEN,
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
                buch.darfBauen(WELT, bert, Tat.BAUEN, x, z), "vorher");

        lager.mitbauerSetzen(stadt.id(), java.util.Set.of(bert));

        // Die Mitbauer kommen aus DERSELBEN Zeile wie der Eigentuemer - die Regel fragt
        // Parzelle.darfBauen und nicht noch einmal selbst nach. Ein zweiter Ort fuer
        // "wer darf hier" liefe irgendwann auseinander.
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT,
                buch.darfBauen(WELT, bert, Tat.BAUEN, x, z));
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
                buch.darfBauen(WELT, null, Tat.BAUEN, buch.plan().feldMitte(stadt.x()),
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
                kasse, stadtkasse, WARPS, VORGABE);
        Stadtfeldplan plan = gezaehlt.plan();

        assertEquals(Freistadtbuch.Bauurteil.LEERE, gezaehlt.darfBauen(WELT, anna, Tat.BAUEN,
                plan.feldAnfang(1) + Stadtfeldplan.FELD_KANTE, plan.feldMitte(1)));
        assertEquals(Freistadtbuch.Bauurteil.AUSSERHALB,
                gezaehlt.darfBauen(WELT, anna, Tat.BAUEN,
                plan.feldMitte(gezaehlt.grenze().bis() + 1), plan.feldMitte(1)));
        assertEquals(Freistadtbuch.Bauurteil.ANKUNFTSFELD,
                gezaehlt.darfBauen(WELT, anna, Tat.BAUEN,
                plan.feldMitte(0), plan.feldMitte(0)));
        assertEquals(0, zaehler.gezaehlt(),
                "keine dieser drei Lagen darf die Ablage anfassen");

        // Und auf einem Stadtfeld GENAU EINMAL - dort ist die Frage unvermeidlich, aber
        // eine zweite waere ein doppelter Gang auf dem heissen Pfad.
        gezaehlt.darfBauen(WELT, anna, Tat.BAUEN, plan.feldMitte(1), plan.feldMitte(1));
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
        staedtekasse = new SpeicherKasse(0L);
        stadtkasse = new Stadtkasse(staedtekasse);
        buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse, stadtkasse,
                WARPS, VORGABE);

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

    @Test
    @DisplayName("Eine neu gegruendete Stadt bekommt die VORGABE aus der config.yml")
    void gruendenSetztDieVorgabe() {
        // FUND DER EINSPRITZUNGSRUNDE ZU TEIL D [12.11.2026]: Dass gruende() die
        // Vorgabe-Buergerrechte auf die frische Stadt schreibt, war von keiner Probe
        // gedeckt. Ein Betreiber, der freistaedte.buergerrechte setzt, haette es nie
        // bemerkt: Die Zeile steht in der Datei, der Startbericht nennt sie, und jede
        // neue Stadt gibt trotzdem nichts her.
        Wirtschaftsstand mitVorgabe = new Wirtschaftsstand(
                Wirtschaftswerte.VORGABE.mitFreistaedten(
                        Wirtschaftswerte.VORGABE.freistaedte()
                                .mitBuergerrechten(Set.of(Tat.BENUTZEN))));
        Freistadtbuch eigenes = new Freistadtbuch(Feldgrenze.freistaedte(), lager,
                kasse, stadtkasse, WARPS, mitVorgabe);

        Freistadtbuch.Entscheid entscheid = eigenes.gruende(WELT, anna, "annastadt",
                VIELE);

        assertEquals(Set.of(Tat.BENUTZEN),
                entscheid.stadt().orElseThrow().buergerrechte(),
                "der ENTSCHEID traegt die Stadt, wie sie NACHHER ist - derselbe Fund"
                        + " wie bei biete() in Teil C3");
        assertEquals(Set.of(Tat.BENUTZEN),
                lager.finde(WELT, entscheid.stadt().orElseThrow().x(),
                        entscheid.stadt().orElseThrow().z()).orElseThrow()
                        .buergerrechte(),
                "und die ABLAGE auch - sonst stuende es nur im Satz an den Spieler");
        assertFalse(entscheid.stadt().orElseThrow().buergerDuerfen(Tat.BAUEN),
                "und nur DAS eine Recht aus der Vorgabe");
    }

    @Test
    @DisplayName("Ohne Vorgabe wird beim Gruenden gar nichts gesetzt")
    void ohneVorgabeNichts() {
        // Die Gegenrichtung, und sie ist der ausgelieferte Zustand: Ab Werk ist die
        // Liste leer, und dann faellt der zweite Schreibzug ganz weg.
        Freistadtbuch.Entscheid entscheid = buch.gruende(WELT, anna, "annastadt", VIELE);

        assertEquals(Parzelle.KEINE_RECHTE,
                entscheid.stadt().orElseThrow().buergerrechte(),
                "eine frische Stadt gibt nichts her, solange der Betreiber nichts"
                        + " einstellt");
    }

    // ------------------------------------- Das Buergerrecht [Teil D4]

    /**
     * Annastadt mit einem Grundstueck, das BERT gehoert - und die Stelle darauf.
     *
     * @return Blockkoordinaten {x, z} MITTEN auf Berts Grundstueck und eine Zelle
     *         daneben, also im Gemeinschaftsgebiet: {aufSeinem_x, aufSeinem_z,
     *         daneben_x, daneben_z}
     */
    private int[] annastadtMitBertsGrundstueck(SpeicherGrundstuecke grundstuecke) {
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);
        buch.gibGrundstuecke(grundstuecke);
        Stadtfeldplan plan = buch.plan();
        int zelleX = plan.ersteZelle(stadt.x());
        int zelleZ = plan.ersteZelle(stadt.z());
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(zelleX, zelleZ),
                bert, 5_000L);
        return new int[] {
            plan.zellenAnfang(zelleX) + 3, plan.zellenAnfang(zelleZ) + 3,
            plan.zellenAnfang(zelleX + 1) + 3, plan.zellenAnfang(zelleZ) + 3,
        };
    }

    /** Die Rechte dieser Stadt neu setzen - ueber das Lager, wie im Spiel. */
    private void stadtGibtHer(Set<Tat> rechte) {
        lager.buergerrechteSetzen(buch.staedte(WELT).get(0).id(), rechte);
    }

    @Test
    @DisplayName("Ein STADTBUERGER darf im Gemeinschaftsgebiet, was seine Stadt hergibt")
    void stadtbuergerDarfWasDieStadtHergibt() {
        // DIE TRAGENDE ZUSAGE VON TEIL D. Bert hat ein Grundstueck in Annastadt und ist
        // damit Stadtbuerger. Was er AUSSERHALB seines Grundstuecks darf, stellt Anna
        // ein - und ohne diese Probe waere das eine Einstellung, die nichts tut.
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        int[] orte = annastadtMitBertsGrundstueck(grundstuecke);
        int danebenX = orte[2];
        int danebenZ = orte[3];

        // Ab Werk gibt die Stadt nichts her.
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, bert, Tat.BAUEN, danebenX, danebenZ),
                "eine frische Stadt gibt nichts her - auch nicht ihren Buergern");

        stadtGibtHer(Set.of(Tat.BENUTZEN));

        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_STADTBUERGER,
                buch.darfBauen(WELT, bert, Tat.BENUTZEN, danebenX, danebenZ),
                "jetzt darf er die Tuer bedienen");
        // JE TAT VERSCHIEDEN, und das ist der ganze Zweck der Tat im Urteil: Dieselbe
        // Stelle, derselbe Spieler, ein anderer Griff - und ein anderes Urteil. Ohne
        // diese Zeile waere eine Wache, die jedes Recht als "alles" liest, gruen.
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, bert, Tat.BAUEN, danebenX, danebenZ),
                "bauen hat Anna NICHT hergegeben");
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, bert, Tat.BEHAELTER, danebenX, danebenZ),
                "und Truhen auch nicht");
    }

    @Test
    @DisplayName("Wer KEIN Grundstueck hat, ist kein Buerger - auch wenn die Stadt"
            + " hergibt")
    void fremderIstKeinBuerger() {
        UUID clara = UUID.randomUUID();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        int[] orte = annastadtMitBertsGrundstueck(grundstuecke);
        stadtGibtHer(Set.of(Tat.BAUEN, Tat.BENUTZEN, Tat.BEHAELTER));

        // Clara hat in Annastadt nichts. Mit allen drei Rechten offen muss sie trotzdem
        // abgewiesen werden - sonst waere "Buergerrecht" nur ein anderes Wort fuer
        // "offene Stadt".
        assertEquals(Freistadtbuch.Bauurteil.FREMDE_STADT,
                buch.darfBauen(WELT, clara, Tat.BENUTZEN, orte[2], orte[3]),
                "Buerger ist, wem hier Grund gehoert - nicht, wer vorbeikommt");
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_STADTBUERGER,
                buch.darfBauen(WELT, bert, Tat.BENUTZEN, orte[2], orte[3]),
                "Bert dagegen schon - sonst prueft die Zeile darueber nichts");
    }

    @Test
    @DisplayName("Das Buergerrecht sticht die STADT, aber nicht ein fremdes GRUNDSTUECK")
    void buergerrechtStichtNichtDasGrundstueck() {
        // DIE GRENZE DES BUERGERRECHTS, und sie ist die Zusage, fuer die ein Kaeufer
        // zahlt: Clara bekommt ein zweites Grundstueck in derselben Stadt und ist damit
        // auch Buergerin. Auf BERTS Grundstueck darf sie trotzdem nichts - sonst waere
        // Teil C2 fuer jeden Buerger der Stadt aufgehoben, und das Grundstueck waere
        // nichts mehr wert.
        UUID clara = UUID.randomUUID();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        int[] orte = annastadtMitBertsGrundstueck(grundstuecke);
        Parzelle stadt = buch.staedte(WELT).get(0);
        Stadtfeldplan plan = buch.plan();
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                        plan.ersteZelle(stadt.x()) + 2, plan.ersteZelle(stadt.z())),
                clara, 5_000L);
        stadtGibtHer(Set.of(Tat.BAUEN, Tat.BENUTZEN, Tat.BEHAELTER));

        assertEquals(Freistadtbuch.Bauurteil.FREMDES_GRUNDSTUECK,
                buch.darfBauen(WELT, clara, Tat.BAUEN, orte[0], orte[1]),
                "auf dem vergebenen Grundstueck eines anderen darf auch eine"
                        + " Mitbuergerin nichts");
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_STADTBUERGER,
                buch.darfBauen(WELT, clara, Tat.BAUEN, orte[2], orte[3]),
                "daneben, im Gemeinschaftsgebiet, darf sie - sonst prueft die Zeile"
                        + " darueber nichts");
    }

    @Test
    @DisplayName("Die STADTBESITZERIN bleibt ERLAUBT - ihr Recht kommt nicht aus der"
            + " Buergerschaft")
    void dieBesitzerinBleibtErlaubt() {
        // Anna hat auf ihrem Feld jedes zugeschnittene, noch nicht verkaufte Grundstueck
        // - sie waere nach der Ablagefrage also "Besitzerin von Grund in dieser Stadt".
        // Ihr Urteil muss trotzdem ERLAUBT bleiben und nicht ERLAUBT_STADTBUERGER
        // werden: Sonst haenge ihr Recht an einer Einstellung, die sie selbst setzt, und
        // ein Stadtbesitzer koennte sich aus seiner eigenen Stadt aussperren.
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        int[] orte = annastadtMitBertsGrundstueck(grundstuecke);
        stadtGibtHer(Set.of());

        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT,
                buch.darfBauen(WELT, anna, Tat.BAUEN, orte[2], orte[3]),
                "auf ihrem eigenen Feld baut sie, ohne sich ein Recht zu geben");
        assertFalse(Stadtbuergerschaft.istBuerger(buch.staedte(WELT).get(0), anna, true),
                "und Buergerin ihrer eigenen Stadt ist sie nicht - daran haengt die"
                        + " Verfallsregel aus Plan 16");
    }

    @Test
    @DisplayName("istStadtbuerger ist die EINE Frage - und ohne Ablage ist niemand"
            + " Buerger")
    void istStadtbuergerIstDieEineFrage() {
        // Dieselbe Methode fragt die Bauregel bei jedem Block und die Stadtwache fuer
        // den Handbalken. Dass sie OHNE Grundstuecksablage nein sagt, ist der Stand vor
        // Teil C2 und in jeder Probe, die das Buch ohne Ablage baut - ein Wurf dort
        // waere eine Wache, die in einer alten Welt bei jedem Block stirbt.
        buch.gruende(WELT, anna, "annastadt", VIELE);
        Parzelle stadt = buch.staedte(WELT).get(0);

        assertFalse(buch.istStadtbuerger(WELT, stadt, bert),
                "ohne Ablage gibt es keine Grundstuecke, also auch keine Buerger");

        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        Stadtfeldplan plan = buch.plan();
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()), plan.ersteZelle(stadt.z())), bert, 0L);

        assertTrue(buch.istStadtbuerger(WELT, stadt, bert));
        assertFalse(buch.istStadtbuerger(WELT, stadt, null), "und niemand auch nicht");

        // UND DIE STADTBESITZERIN - mit einem eigenen Grundstueck in der Hand. FUND DER
        // EINSPRITZUNGSRUNDE [12.11.2026]: Hier stand dieselbe Zusage, aber Anna hatte
        // in dieser Probe nichts. Dann sagt auch eine Fassung nein, die nur den BESITZ
        // fragt und die Regel ueberspringt - das Gegenbeispiel kam nicht an. Erst mit
        // Grund in der Hand prueft die Zeile, was sie behauptet.
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()) + 1, plan.ersteZelle(stadt.z())), anna, 0L);
        assertTrue(grundstuecke.besitztIn(WELT, stadt.id(), anna),
                "ihr gehoert jetzt Grund in ihrer Stadt - sonst prueft die Zeile"
                        + " darunter wieder nichts");
        assertFalse(buch.istStadtbuerger(WELT, stadt, anna),
                "trotzdem ist sie keine Buergerin ihrer eigenen Stadt - die Frage geht"
                        + " durch Stadtbuergerschaft.istBuerger und nicht an ihr vorbei");
    }


    // ------------------------------------- Der Regeltext [Teil E2]

    /**
     * Eine Kasse, die den Stand NENNT und beim Abbuchen WIRFT.
     *
     * <p>Nur damit ist zu sehen, ob die Vorpruefung vor dem Abbuchen steht: Mit ihr
     * kommt ZU_WENIG, ohne sie GESTOERT. Eine Kasse, die bloss false gibt, zeigte den
     * Unterschied nicht - beide Riegel antworten gleich.
     */
    private static final class WerfendeKasse extends Kassenattrappe {

        private long stand;
        private int versuche;

        WerfendeKasse() {
            super(0L);
        }

        @Override
        public long guthaben(UUID spieler) {
            return stand;
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            versuche++;
            throw new IllegalStateException("Die Datenbank antwortet nicht");
        }
    }

    /**
     * Ein Lager, das jeden gesetzten Besuchstag MITSCHREIBT.
     *
     * <p>Der Tag und keine Zahl: Eine Zaehltafel sagte nur, DASS geschrieben wurde,
     * nicht WAS - und genau darauf kommt es an, wenn zwei Stempel am selben Tag einer
     * bleiben sollen.
     */
    private static final class StempelLager extends Lagerattrappe {

        private final List<LocalDate> gesetzt = new java.util.ArrayList<>();

        @Override
        public void besuchSetzen(long id, LocalDate tag) {
            gesetzt.add(tag);
            super.besuchSetzen(id, tag);
        }
    }

    /** Eine gegruendete Stadt - und zwar ueber das Buch, wie im Spiel. */
    private Parzelle gegruendet() {
        buch.gruende(WELT, anna, "annastadt", VIELE);
        return buch.staedte(WELT).get(0);
    }

    /** Die Blockmitte eines Stadtfeldes auf der X-Achse. */
    private int mitteX(Parzelle stadt) {
        return buch.plan().feldMitte(stadt.x());
    }

    /** Dieselbe Mitte auf der Z-Achse. */
    private int mitteZ(Parzelle stadt) {
        return buch.plan().feldMitte(stadt.z());
    }

    @Test
    @DisplayName("Regeln setzen, aendern, loeschen - und der Entscheid traegt den Stand")
    void regelnSetzenUndLoeschen() {
        Parzelle stadt = gegruendet();

        Freistadtbuch.Regelentscheid gesetzt = buch.setzeRegeln(WELT, anna,
                mitteX(stadt), mitteZ(stadt), "Kein PvP.");

        assertEquals(Freistadtbuch.Regelbescheid.GESETZT, gesetzt.bescheid());
        assertEquals("Kein PvP.", gesetzt.stadt().orElseThrow().regeln(),
                "der ENTSCHEID traegt die Stadt, wie sie NACHHER ist - derselbe Fund"
                        + " wie bei biete() in Teil C3");
        assertEquals("Kein PvP.",
                lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow().regeln(),
                "und die ABLAGE auch - sonst stuende es nur im Satz an den Spieler");

        // Derselbe Text nochmal: kein Schreibzug fuer nichts.
        assertEquals(Freistadtbuch.Regelbescheid.SCHON_SO,
                buch.setzeRegeln(WELT, anna, mitteX(stadt), mitteZ(stadt),
                        "Kein PvP.").bescheid());

        // Und der Weg zurueck.
        Freistadtbuch.Regelentscheid weg = buch.setzeRegeln(WELT, anna, mitteX(stadt),
                mitteZ(stadt), null);
        assertEquals(Freistadtbuch.Regelbescheid.GELOESCHT, weg.bescheid());
        assertFalse(weg.stadt().orElseThrow().hatRegeln());
        assertEquals(Freistadtbuch.Regelbescheid.WAR_SCHON_LEER,
                buch.setzeRegeln(WELT, anna, mitteX(stadt), mitteZ(stadt), " ")
                        .bescheid(),
                "und ein zweites Loeschen sagt es - Leerraum loescht genauso wie null");
    }

    @Test
    @DisplayName("Nur der BESITZER setzt die Regeln - nicht einmal ein Mitbauer")
    void regelnNurDerBesitzer() {
        Parzelle stadt = gegruendet();
        lager.mitbauerSetzen(stadt.id(), java.util.Set.of(bert));

        Freistadtbuch.Regelentscheid fremd = buch.setzeRegeln(WELT, bert,
                mitteX(stadt), mitteZ(stadt), "Meine Regel.");

        assertEquals(Freistadtbuch.Regelbescheid.FREMDE_STADT, fremd.bescheid());
        assertEquals(stadt.name(), fremd.stadt().orElseThrow().name(),
                "die Stadt kommt MIT - der Satz nennt ihren Namen und ihren Besitzer");
        assertFalse(lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow().hatRegeln(),
                "und geschrieben wurde nichts. Ein MITBAUER wurde zum Bauen"
                        + " eingetragen, nicht zum Regeln aufstellen - sonst setzt"
                        + " jeder Helfer die Hausordnung um.");
    }

    @Test
    @DisplayName("Ein zu langer Text wird VOR dem Blick in die Ablage abgewiesen")
    void zuLangerTextVorDerAblage() {
        // DIE SORTIERUNG NACH KOSTEN, wie bei gruende(): Wer sich verschreibt, soll das
        // nicht erst erfahren, nachdem die Datenbank gefragt wurde. Gemessen wird es an
        // der Stadt, die NICHT gebraucht wird - der Entscheid traegt keine.
        Freistadtbuch.Regelentscheid zuLang = buch.setzeRegeln(WELT, anna, 0, 0,
                "x".repeat(Parzelle.REGELN_GROESSTENS + 1));

        assertEquals(Freistadtbuch.Regelbescheid.ZU_LANG, zuLang.bescheid());
        assertTrue(zuLang.stadt().isEmpty(),
                "und die Ablage wurde gar nicht gefragt - sonst stuende hier die Stadt");
    }

    @Test
    @DisplayName("Ohne Stadt und mit stummer Ablage: die zwei Absagen")
    void regelnOhneStadtUndGestoert() {
        // Auf dem Ankunftsfeld 0/0 steht keine Stadt.
        assertEquals(Freistadtbuch.Regelbescheid.KEINE_STADT,
                buch.setzeRegeln(WELT, anna, 0, 0, "Kein PvP.").bescheid());

        Freistadtbuch stumm = new Freistadtbuch(Feldgrenze.freistaedte(),
                new Lagerattrappe.Stumm(), kasse, stadtkasse, WARPS, VORGABE);
        assertEquals(Freistadtbuch.Regelbescheid.GESTOERT,
                stumm.setzeRegeln(WELT, anna, 48, 48, "Kein PvP.").bescheid());
    }


    // ------------------------------------- Die Auflage [Teil E3]

    @Test
    @DisplayName("Die vier Zustaende der Auflage - und jeder sagt, WAS fehlt")
    void dieVierZustaendeDerAuflage() {
        // VIER Zustaende und nicht ein Ja-Nein: Der Satz an den Stadtbesitzer soll
        // sagen, was ihm fehlt. Mit einem boolean muesste der Aufrufer beide
        // Bedingungen erneut fragen - zwei Wege zur selben Frage.
        Parzelle stadt = gegruendet();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);

        // Frisch gegruendet: beides fehlt. Das ist der Anfangszustand JEDER Stadt.
        assertEquals(Freistadtbuch.Auflage.OHNE_BEIDES, buch.auflage(stadt));
        assertFalse(buch.auflage(stadt).erfuellt());

        // Nur Regeln.
        lager.regelnSetzen(stadt.id(), "Kein PvP.");
        Parzelle mitRegeln = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();
        assertEquals(Freistadtbuch.Auflage.OHNE_BUERGER, buch.auflage(mitRegeln));

        // Nur ein Buerger - BERT bekommt ein Grundstueck, nicht Anna.
        lager.regelnSetzen(stadt.id(), null);
        Parzelle ohneRegeln = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();
        Stadtfeldplan plan = buch.plan();
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()), plan.ersteZelle(stadt.z())), bert, 5_000L);
        assertEquals(Freistadtbuch.Auflage.OHNE_REGELN, buch.auflage(ohneRegeln));

        // Und beides.
        lager.regelnSetzen(stadt.id(), "Kein PvP.");
        Parzelle fertig = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();
        assertEquals(Freistadtbuch.Auflage.ERFUELLT, buch.auflage(fertig));
        assertTrue(buch.auflage(fertig).erfuellt());
    }

    @Test
    @DisplayName("Ein Grundstueck des BESITZERS erfuellt die Auflage NICHT")
    void eigenesGrundstueckZaehltNicht() {
        // DIE ENTSCHEIDUNG AUS TEIL D, auf der die halbe Auflage steht: Der
        // Stadtbesitzer ist kein Buerger seiner eigenen Stadt. Zaehlte sein eigener
        // Zuschnitt mit, haette jede Stadt nach dem ERSTEN Schnitt die Auflage erfuellt
        // - und der Plan verhindert damit gar nichts mehr.
        Parzelle stadt = gegruendet();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        lager.regelnSetzen(stadt.id(), "Kein PvP.");
        Stadtfeldplan plan = buch.plan();

        // ANNA schneidet sich selbst ein Grundstueck zu - so faengt jede Stadt an.
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()), plan.ersteZelle(stadt.z())), anna, 0L);

        Parzelle danach = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();
        assertEquals(Freistadtbuch.Auflage.OHNE_BUERGER, buch.auflage(danach),
                "ihr eigener Zuschnitt macht sie nicht zur Buergerin - sonst waere die"
                        + " Auflage mit drei Befehlen und ohne einen einzigen Besucher"
                        + " erfuellt");
    }

    @Test
    @DisplayName("Besitzer, Mitbauer und Stadtbuerger duerfen IMMER hinein")
    void dreiRollenDuerfenImmer() {
        // DIE HENNE-EI-FALLE, und ohne diese Ausnahme ist die Gruendung eine Falle
        // ueber 400.000 Mark: Eine frische Stadt hat nie ein vergebenes Grundstueck,
        // ihr Besitzer kaeme also nicht hinein, um eines zuzuschneiden.
        // clara LOKAL, wie in den beiden anderen Proben dieser Datei: Eine dritte
        // Kennung als Feld waere ein Zustand, den jede Probe mitschleppt.
        UUID clara = UUID.randomUUID();
        Parzelle stadt = gegruendet();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        lager.mitbauerSetzen(stadt.id(), java.util.Set.of(bert));
        Stadtfeldplan plan = buch.plan();
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()) + 1, plan.ersteZelle(stadt.z())),
                clara, 5_000L);
        Parzelle jetzt = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();

        // Die Auflage ist OFFEN - es fehlt der Regeltext.
        assertEquals(Freistadtbuch.Auflage.OHNE_REGELN, buch.auflage(jetzt));

        assertTrue(buch.darfReisen(jetzt, anna).erfuellt(),
                "die BESITZERIN - sonst kommt sie nicht hinein, um Regeln zu setzen");
        assertTrue(buch.darfReisen(jetzt, bert).erfuellt(),
                "ihr MITBAUER - er wurde genau zum Bauen eingetragen");
        assertTrue(buch.darfReisen(jetzt, clara).erfuellt(),
                "und die STADTBUERGERIN - ihr bezahltes Grundstueck darf ihr nicht"
                        + " unerreichbar werden, weil die Stadt eine Auflage verliert");

        // Ein Fremder nicht - und der Grund kommt mit.
        assertEquals(Freistadtbuch.Auflage.OHNE_REGELN,
                buch.darfReisen(jetzt, UUID.randomUUID()),
                "ein FREMDER bleibt draussen, und der Satz an ihn nennt, was fehlt");
    }

    @Test
    @DisplayName("Mit abgeschalteter Auflage darf JEDER - und das ist einstellbar")
    void abgeschalteteAuflageLaesstJeden() {
        // Die Einstellung aus der config.yml, am GEBRAUCH gefragt: Ein Betreiber, der
        // sie abschaltet, soll das ohne Programmaenderung koennen - und ohne Neustart.
        Freistadtbuch ohneAuflage = new Freistadtbuch(Feldgrenze.freistaedte(), lager,
                kasse, stadtkasse, WARPS,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitFreistaedten(
                        Freistadtwerte.VORGABE.mitAuflageTeleporter(false))));
        ohneAuflage.gruende(WELT, anna, "offenstadt", VIELE);
        Parzelle stadt = ohneAuflage.staedte(WELT).get(0);
        ohneAuflage.gibGrundstuecke(new SpeicherGrundstuecke());

        assertEquals(Freistadtbuch.Auflage.OHNE_BEIDES, ohneAuflage.auflage(stadt),
                "die Auflage ist OFFEN - abgeschaltet ist nur ihre WIRKUNG");
        assertTrue(ohneAuflage.darfReisen(stadt, UUID.randomUUID()).erfuellt(),
                "und ein Fremder kommt trotzdem hinein");
    }

    @Test
    @DisplayName("OHNE Grundstuecksablage darf jeder hinein - die Regel faellt aus")
    void ohneAblageDarfJeder() {
        // DIE UNANGENEHME, ABER RICHTIGE ANTWORT: Faellt die Grundstuecksablage beim
        // Start aus, weiss niemand, ob ein Grundstueck vergeben ist. Die "sichere"
        // Richtung hiesse dann, dass in diesem Lauf niemand in IRGENDEINE Freistadt
        // kommt - und der Riegel schuetzt dabei nichts, denn der Bauschutz haengt nicht
        // an ihm. Eine Wirtschaftsregel darf bei ihrem Ausfall nicht den Zugang kosten.
        //
        // DAS BUCH HIER HAT KEINE ABLAGE - gibGrundstuecke wurde nie gerufen, genau wie
        // nach einem Ausfall beim Start.
        Parzelle stadt = gegruendet();

        assertEquals(Freistadtbuch.Auflage.OHNE_BEIDES, buch.auflage(stadt),
                "ohne Ablage ist niemand Buerger - dieselbe Lesart wie in"
                        + " istStadtbuerger");
        assertTrue(buch.darfReisen(stadt, UUID.randomUUID()).erfuellt(),
                "und trotzdem kommt jeder hinein. Dass der Startbericht das SAGT, ist"
                        + " die Bedingung dafuer - eine Regel, die halb wirkt und davon"
                        + " schweigt, waere der schlimmere Fehler.");
    }

    @Test
    @DisplayName("Das LADENRECHT sperrt auch die Besitzerin - anders als die Reise")
    void ladenrechtSperrtAuchDieBesitzerin() {
        // DER EINE SATZ, DER DIE BEIDEN RIEGEL UNTERSCHEIDET. Beim Teleporter muss die
        // Besitzerin hinein, sonst ist die Gruendung eine 400.000-Mark-Falle. Beim
        // Ladenrecht sperrt sie nichts ein: Regeln setzt sie mit einem Befehl, ein
        // Grundstueck schneidet sie zu und bietet es an - beides ohne einen Laden.
        //
        // Liesse man sie aus, baute sie ihre ganze Markthalle und erfuellte die Auflage
        // nie. Dann traefe der Riegel nur noch ihre Buerger, und gegen die Geisterstadt
        // waere er blind.
        //
        // DIESE PROBE MISST EINEN UNTERSCHIED, und darum stehen beide Fragen hier
        // nebeneinander: Waeren sie in zwei Proben, bliebe die eine gruen, wenn jemand
        // die Ausnahme aus Versehen auch in darfLaden einbaut.
        Parzelle stadt = gegruendet();
        buch.gibGrundstuecke(new SpeicherGrundstuecke());

        assertEquals(Freistadtbuch.Auflage.OHNE_BEIDES, buch.auflage(stadt),
                "frisch gegruendet - beides fehlt");
        assertTrue(buch.darfReisen(stadt, anna).erfuellt(),
                "REISEN darf sie: sonst kaeme sie nicht in ihre eigene Stadt");
        assertEquals(Freistadtbuch.Auflage.OHNE_BEIDES, buch.darfLaden(stadt),
                "LADEN darf sie nicht - und der Grund kommt mit, damit der Satz an sie"
                        + " nennt, was ihr fehlt");
    }

    @Test
    @DisplayName("Mit erfuellter Auflage darf die Stadt Laeden")
    void erfuellteAuflageGibtDasLadenrecht() {
        Parzelle stadt = gegruendet();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        lager.regelnSetzen(stadt.id(), "Kein PvP.");
        Stadtfeldplan plan = buch.plan();
        // BERT und nicht anna: Ein Grundstueck der Besitzerin macht keinen Buerger.
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()), plan.ersteZelle(stadt.z())), bert, 5_000L);
        Parzelle fertig = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();

        assertTrue(buch.darfLaden(fertig).erfuellt(),
                "Regeltext und ein vergebenes Grundstueck - mehr verlangt die Auflage"
                        + " nicht");
    }

    @Test
    @DisplayName("Die zwei Schalter sind getrennt - Teleporter aus laesst das Ladenrecht")
    void dieZweiSchalterSindGetrennt() {
        // ZWEI SCHALTER FUER EINE AUFLAGE, und diese Probe haelt sie auseinander. Ein
        // Betreiber, der den Teleporter oeffnet, hat damit noch nichts ueber Laeden
        // gesagt - und umgekehrt. Mit einem gemeinsamen Schalter waere die zweite
        // Wirkung ein stilles Mitbringsel der ersten.
        Freistadtbuch ohneTeleporter = mitAuflage(
                Freistadtwerte.VORGABE.mitAuflageTeleporter(false));
        Parzelle eine = gruendeIn(ohneTeleporter, "offenstadt");
        assertTrue(ohneTeleporter.darfReisen(eine, UUID.randomUUID()).erfuellt(),
                "der Teleporter ist offen");
        assertEquals(Freistadtbuch.Auflage.OHNE_BEIDES, ohneTeleporter.darfLaden(eine),
                "das Ladenrecht aber NICHT - es haengt an seinem eigenen Schalter");

        Freistadtbuch ohneLadenrecht = mitAuflage(
                Freistadtwerte.VORGABE.mitAuflageLadenrecht(false));
        Parzelle andere = gruendeIn(ohneLadenrecht, "ladenstadt");
        assertTrue(ohneLadenrecht.darfLaden(andere).erfuellt(),
                "hier darf die Stadt Laeden, obwohl die Auflage offen ist");
        assertEquals(Freistadtbuch.Auflage.OHNE_BEIDES,
                ohneLadenrecht.darfReisen(andere, UUID.randomUUID()),
                "und der Teleporter bleibt trotzdem zu");
    }

    @Test
    @DisplayName("OHNE Grundstuecksablage faellt das Ladenrecht aus - keine Vollsperre")
    void ohneAblageFaelltDasLadenrechtAus() {
        // DER FUND, DER darfLaden NOETIG MACHT - und der Grund, warum der Riegel nicht
        // einfach auflage(stadt) fragt: Ohne Grundstuecksablage ist hatStadtbuerger
        // immer false, also kann auflage() NIE ERFUELLT geben. Ein Riegel ohne diesen
        // Zweig verweigerte bei stummer Datenbank in JEDER Freistadt jeden neuen
        // Kistenshop - eine Wirtschaftsregel, die beim Ausfall zur Vollsperre wird.
        //
        // Das Buch hier hat keine Ablage: gibGrundstuecke wurde nie gerufen, genau wie
        // nach einem Ausfall beim Start.
        Parzelle stadt = gegruendet();

        assertEquals(Freistadtbuch.Auflage.OHNE_BEIDES, buch.auflage(stadt),
                "ohne Ablage ist niemand Buerger");
        assertTrue(buch.darfLaden(stadt).erfuellt(),
                "und trotzdem darf hier ein Laden entstehen. Dass der Startbericht das"
                        + " SAGT, ist die Bedingung dafuer - sonst ist es eine Regel,"
                        + " die halb wirkt und davon schweigt.");
    }

    /**
     * Ein zweites Buch mit anderen Freistadtwerten - und mit EIGENEM Lager.
     *
     * <p>Eigenes Lager, weil {@link #gruendeIn} die Stadt mit {@code get(0)} holt: Auf
     * einem geteilten Lager saehe das zweite Buch die Stadt des ersten und traefe die
     * falsche. Die Probe waere dann gruen, ohne den zweiten Schalter je anzufassen.
     *
     * <p>MIT Grundstuecksablage, und das ist hier wesentlich: Ohne sie fallen BEIDE
     * Riegel aus, und dann sehen die zwei Schalter gleich aus - gruen, egal was die
     * Probe misst.
     */
    private Freistadtbuch mitAuflage(Freistadtwerte werte) {
        Freistadtbuch anderes = new Freistadtbuch(Feldgrenze.freistaedte(),
                new SpeicherLager(), kasse, stadtkasse, WARPS,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitFreistaedten(werte)));
        anderes.gibGrundstuecke(new SpeicherGrundstuecke());
        return anderes;
    }

    /** Gruendet eine Stadt in einem fremden Buch und gibt sie zurueck. */
    private Parzelle gruendeIn(Freistadtbuch anderes, String name) {
        anderes.gruende(WELT, anna, name, VIELE);
        return anderes.staedte(WELT).get(0);
    }

    // ------------------------------------- Das Ende einer Stadt [Teil E6]

    @Test
    @DisplayName("Aufgegeben: die Stadt ist weg, der Anteil UND die Kasse sind da")
    void aufgegeben() {
        // DIE KASSE GEHT MIT, und ohne diese Zeile waere ihr Guthaben verloren: Das
        // Konto bleibt bestehen, aber der Weg dorthin fuehrt ueber die Stadtzeile - und
        // die gibt es nicht mehr.
        Parzelle stadt = gegruendet();
        stadtkasse.einzahlen(stadt, 1_250L, Buchungsgrund.STADTKASSE_GEBUEHR);
        long vorher = kasse.guthaben(anna);

        Freistadtbuch.Endeentscheid entscheid = buch.gibAuf(WELT, anna, mitteX(stadt),
                mitteZ(stadt));

        assertEquals(Freistadtbuch.Endebescheid.BEENDET, entscheid.bescheid());
        // Die HAELFTE von 400.000 - die Vorgabe. Vom EINGESETZTEN Preis der Zeile.
        assertEquals(PREIS / 2, entscheid.erstattung());
        assertEquals(1_250L, entscheid.kasse());
        assertEquals(PREIS / 2 + 1_250L, entscheid.zusammen());
        assertEquals(vorher + PREIS / 2 + 1_250L, kasse.guthaben(anna),
                "und beides ist wirklich angekommen - ein Bescheid allein ist eine"
                        + " Behauptung");
        assertTrue(lager.finde(WELT, stadt.x(), stadt.z()).isEmpty(),
                "das Feld ist wieder frei");
        assertEquals(0L, staedtekasse.guthaben(Stadtkasse.konto(stadt)),
                "und die Kasse ist leer - nicht nur ausgezahlt, sondern auch"
                        + " abgebucht");
    }

    @Test
    @DisplayName("Eine Stadt mit FREMDEM Grundstueck laesst sich nicht aufgeben")
    void nochBuergerHaeltAn() {
        // DER RIEGEL, OHNE DEN DIESER BEFEHL EIN DIEBSTAHL WAERE: Ein Stadtbuerger hat
        // sein Grundstueck BEZAHLT. Wer die Stadt darueber hinweg aufloeste, naehme ihm
        // Grund und Bauten und gaebe ihm nichts - fuer eine Entscheidung, die ein
        // anderer getroffen hat.
        Parzelle stadt = gegruendet();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        Stadtfeldplan plan = buch.plan();
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()), plan.ersteZelle(stadt.z())), bert, 5_000L);
        long vorher = kasse.guthaben(anna);

        assertEquals(Freistadtbuch.Endebescheid.NOCH_BUERGER,
                buch.gibAuf(WELT, anna, mitteX(stadt), mitteZ(stadt)).bescheid());

        assertTrue(lager.finde(WELT, stadt.x(), stadt.z()).isPresent(),
                "und die Stadt steht noch");
        assertEquals(vorher, kasse.guthaben(anna), "und kein Geld ist geflossen");
    }

    @Test
    @DisplayName("Das EIGENE Grundstueck haelt nicht an - es verschwindet mit")
    void eigenesGrundstueckHaeltNichtAn() {
        // Die Gegenprobe: Jeder Stadtbesitzer schneidet sich selbst Grundstuecke zu,
        // und wenn die ihn an der Aufgabe hinderten, koennte fast niemand aufgeben.
        Parzelle stadt = gegruendet();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        Stadtfeldplan plan = buch.plan();
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()), plan.ersteZelle(stadt.z())), anna, 0L);

        assertEquals(Freistadtbuch.Endebescheid.BEENDET,
                buch.gibAuf(WELT, anna, mitteX(stadt), mitteZ(stadt)).bescheid());

        assertEquals(java.util.List.of(), grundstuecke.inStadt(stadt.id()),
                "und es ist WEG - eine Zeile, die auf eine Stadt zeigt, die es nicht"
                        + " mehr gibt, waere schlimmer als keine");
    }

    @Test
    @DisplayName("Eine FREMDE Stadt gibt niemand auf - und wo keine steht, auch nicht")
    void fremdeStadtUndKeineStadt() {
        Parzelle stadt = gegruendet();

        assertEquals(Freistadtbuch.Endebescheid.FREMDE_STADT,
                buch.gibAuf(WELT, bert, mitteX(stadt), mitteZ(stadt)).bescheid());
        assertEquals(Freistadtbuch.Endebescheid.FREMDE_STADT,
                buch.gibAuf(WELT, null, mitteX(stadt), mitteZ(stadt)).bescheid());
        assertEquals(Freistadtbuch.Endebescheid.KEINE_STADT,
                buch.gibAuf(WELT, anna, buch.plan().feldMitte(40),
                        buch.plan().feldMitte(40)).bescheid());
        assertTrue(lager.finde(WELT, stadt.x(), stadt.z()).isPresent());
    }

    @Test
    @DisplayName("Das Team raeumt nach NAMEN - und das Geld geht an den BESITZER")
    void raeumenZahltDemBesitzer() {
        // Der ganze Unterschied zum Aufgeben: Geraeumt wird fremdes Eigentum, und wer
        // es verliert, bekommt dafuer, was ihm zusteht - auch wenn er gerade nicht da
        // ist. Ein Teammitglied, das sich selbst bezahlte, waere eine Geldquelle.
        Parzelle stadt = gegruendet();
        long annaVorher = kasse.guthaben(anna);
        long bertVorher = kasse.guthaben(bert);

        Freistadtbuch.Endeentscheid entscheid = buch.raeume(WELT, "ANNASTADT");

        assertEquals(Freistadtbuch.Endebescheid.BEENDET, entscheid.bescheid(),
                "der Name wird OHNE Ruecksicht auf Gross und Klein gefunden - wer"
                        + " raeumt, tippt aus einer Liste ab");
        assertEquals(annaVorher + PREIS / 2, kasse.guthaben(anna));
        assertEquals(bertVorher, kasse.guthaben(bert), "und bert bekommt nichts");
    }

    @Test
    @DisplayName("Einen Namen, den es nicht gibt, raeumt niemand")
    void raeumenOhneStadt() {
        gegruendet();

        assertEquals(Freistadtbuch.Endebescheid.KEINE_STADT,
                buch.raeume(WELT, "geisterstadt").bescheid());
        assertEquals(1, buch.staedte(WELT).size());
    }

    @Test
    @DisplayName("Die Rueckfrage rechnet GENAUSO wie die Tat")
    void rueckfrageUndTatRechnenGleich() {
        // Zeigte die Rueckfrage eine andere Zahl als die Tat, waere das Bestaetigen
        // eine Unterschrift unter einen Betrag, der nicht gilt. Darum EINE Methode.
        Parzelle stadt = gegruendet();

        long gezeigt = buch.erstattungFuer(stadt);

        assertEquals(gezeigt, buch.gibAuf(WELT, anna, mitteX(stadt), mitteZ(stadt))
                .erstattung());
    }

    @Test
    @DisplayName("Mit 0 Prozent Erstattung gibt es nichts zurueck - und das ist kein Fehler")
    void nullProzentIstKeinFehler() {
        Freistadtbuch ohne = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                stadtkasse, WARPS, new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitFreistaedten(Freistadtwerte.VORGABE.mitErstattung(0))));
        ohne.gruende(WELT, anna, "sparstadt", VIELE);
        Parzelle stadt = ohne.staedte(WELT).get(0);
        long vorher = kasse.guthaben(anna);

        Freistadtbuch.Endeentscheid entscheid = ohne.gibAuf(WELT, anna, mitteX(stadt),
                mitteZ(stadt));

        assertEquals(Freistadtbuch.Endebescheid.BEENDET, entscheid.bescheid());
        assertEquals(0L, entscheid.zusammen());
        assertEquals(vorher, kasse.guthaben(anna));
        assertTrue(lager.finde(WELT, stadt.x(), stadt.z()).isEmpty());
    }

    @Test
    @DisplayName("Die LADENGEBUEHR geht vom Spieler in die Stadtkasse")
    void ladengebuehrFliesst() {
        Parzelle stadt = gegruendet();
        long vorher = kasse.guthaben(bert);

        Freistadtbuch.Ladengebuehr gebuehr = buch.pruefeLadengebuehr(stadt, bert);

        assertEquals(Freistadtwerte.VORGABE_LADENGEBUEHR_ANLAGE, gebuehr.betrag());
        assertTrue(gebuehr.faellt());
        assertTrue(gebuehr.bezahlbar());

        assertTrue(buch.erhebeLadengebuehr(stadt, bert, gebuehr.betrag()));

        assertEquals(vorher - gebuehr.betrag(), kasse.guthaben(bert));
        assertEquals(gebuehr.betrag(), buch.kassenstand(stadt),
                "ZWEI Buchungen fuer eine Bewegung: beim Spieler eine Ausgabe, in der"
                        + " Stadt eine Einnahme");
    }

    @Test
    @DisplayName("Wer zu wenig hat, zahlt NICHT - und es wird nichts gebucht")
    void ladengebuehrZuTeuer() {
        Parzelle stadt = gegruendet();
        UUID arm = UUID.randomUUID();
        kasse.eroeffne(arm);
        // Leergeraeumt mit einem Grund, der eine AUSGABE ist - BESTAND ist eine
        // Einnahme, und der Satz Buchung weist den Betrag dann ab.
        kasse.abbuchen(arm, kasse.guthaben(arm),
                Buchungsgrund.FREISTADT_GEGRUENDET);

        Freistadtbuch.Ladengebuehr gebuehr = buch.pruefeLadengebuehr(stadt, arm);

        assertTrue(gebuehr.faellt());
        assertFalse(gebuehr.bezahlbar());
        assertEquals(0L, buch.kassenstand(stadt), "und nichts ist geflossen");
    }

    @Test
    @DisplayName("Mit 0 Mark Gebuehr faellt nichts - der Ausschalter")
    void ladengebuehrAbgeschaltet() {
        Freistadtbuch ohne = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                stadtkasse, WARPS, new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitFreistaedten(
                                Freistadtwerte.VORGABE.mitLadengebuehren(0L, 0L))));
        ohne.gruende(WELT, anna, "gratisstadt", VIELE);
        Parzelle stadt = ohne.staedte(WELT).get(0);

        Freistadtbuch.Ladengebuehr gebuehr = ohne.pruefeLadengebuehr(stadt, bert);

        assertFalse(gebuehr.faellt());
        assertTrue(gebuehr.bezahlbar(), "bei 0 ist alles bezahlbar");
    }

    // ------------------------------------- Der Verfall [Teil E5]

    @Test
    @DisplayName("Eine VERFALLENE Stadt sperrt ALLE DREI Ja-Zweige von darfBauen")
    void verfallenSperrtAlleDrei() {
        // DIE EINE PROBE, DIE DEN RIEGEL TRAEGT, und sie prueft alle drei Zweige
        // nebeneinander: den der Besitzerin, den eines Mitbauers und den eines
        // Stadtbuergers mit Grundstueck. Drei getrennte Proben waeren gruen, wenn
        // jemand den Riegel in zwei der drei Zweige einzeln einbaut - und genau so
        // waere er gebaut, wenn er nicht VOR ihnen stuende.
        UUID clara = UUID.randomUUID();
        Parzelle stadt = gegruendet();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        lager.mitbauerSetzen(stadt.id(), java.util.Set.of(bert));
        lager.buergerrechteSetzen(stadt.id(), java.util.Set.of(Tat.BAUEN));
        Stadtfeldplan plan = buch.plan();
        // Claras Grundstueck in der ERSTEN Zelle, gebaut wird in der Feldmitte - also
        // im Gemeinschaftsgebiet, wo das Buergerrecht gilt.
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(
                plan.ersteZelle(stadt.x()), plan.ersteZelle(stadt.z())), clara, 5_000L);
        int x = mitteX(stadt);
        int z = mitteZ(stadt);

        // VORHER duerfen alle drei - sonst prueft die Probe danach nichts.
        assertTrue(buch.darfBauen(WELT, anna, Tat.BAUEN, x, z).erlaubt(), "Besitzerin");
        assertTrue(buch.darfBauen(WELT, bert, Tat.BAUEN, x, z).erlaubt(), "Mitbauer");
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_STADTBUERGER,
                buch.darfBauen(WELT, clara, Tat.BAUEN, x, z), "Stadtbuergerin");

        lager.zustandSetzen(stadt.id(), Zustand.VERFALLEN);

        for (UUID wer : java.util.List.of(anna, bert, clara)) {
            assertEquals(Freistadtbuch.Bauurteil.VERFALLENE_STADT,
                    buch.darfBauen(WELT, wer, Tat.BAUEN, x, z),
                    "auch " + wer + " baut hier nicht mehr");
        }
        assertFalse(Freistadtbuch.Bauurteil.VERFALLENE_STADT.erlaubt());
    }

    @Test
    @DisplayName("Auf dem eigenen GRUNDSTUECK in einer verfallenen Stadt auch nicht")
    void verfallenSperrtAuchDasGrundstueck() {
        // Der vierte Weg, und der engste: Wer sein Grundstueck bezahlt hat, darf dort
        // sonst immer. Dieser Zweig steht in darfBauen VOR den anderen zwei - also
        // braucht er seine eigene Zeile, sonst bleibt er ungeprueft.
        Parzelle stadt = gegruendet();
        SpeicherGrundstuecke grundstuecke = new SpeicherGrundstuecke();
        buch.gibGrundstuecke(grundstuecke);
        Stadtfeldplan plan = buch.plan();
        int zelleX = plan.ersteZelle(stadt.x());
        int zelleZ = plan.ersteZelle(stadt.z());
        grundstuecke.anlegen(WELT, stadt.id(), Zellquader.eineZelle(zelleX, zelleZ),
                bert, 5_000L);
        int x = plan.zellenAnfang(zelleX);
        int z = plan.zellenAnfang(zelleZ);
        assertEquals(Freistadtbuch.Bauurteil.ERLAUBT_GRUNDSTUECK,
                buch.darfBauen(WELT, bert, Tat.BAUEN, x, z), "vorher darf er");

        lager.zustandSetzen(stadt.id(), Zustand.VERFALLEN);

        assertEquals(Freistadtbuch.Bauurteil.VERFALLENE_STADT,
                buch.darfBauen(WELT, bert, Tat.BAUEN, x, z));
    }

    @Test
    @DisplayName("lasseVerfallen schreibt EINMAL und danach nicht mehr")
    void lasseVerfallenSchreibtEinmal() {
        // Jeder Schreibvorgang leert den Zwischenspeicher aller Parzellen. Ein Lauf,
        // der taeglich denselben Zustand neu setzt, leerte ihn fuer nichts.
        Parzelle stadt = gegruendet();

        assertTrue(buch.lasseVerfallen(stadt));
        Parzelle danach = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();
        assertEquals(Zustand.VERFALLEN, danach.zustand());

        assertFalse(buch.lasseVerfallen(danach), "zweimal wird nicht geschrieben");
    }

    @Test
    @DisplayName("Nur der BESITZER belebt - ein Mitbauer und ein Fremder nicht")
    void nurDerBesitzerBelebt() {
        // Ein Fremder, der durch eine verfallene Stadt laeuft, belebte sie sonst fuer
        // ihren abwesenden Besitzer mit - und die Geisterstadt bliebe stehen, solange
        // irgendjemand sie als Abkuerzung benutzt. Ein Mitbauer wurde zum Bauen
        // eingetragen, nicht zum Erhalten.
        Parzelle stadt = gegruendet();
        lager.mitbauerSetzen(stadt.id(), java.util.Set.of(bert));
        buch.lasseVerfallen(stadt);
        Parzelle verfallen = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();

        assertFalse(buch.beleb(verfallen, bert), "der MITBAUER nicht");
        assertFalse(buch.beleb(verfallen, UUID.randomUUID()), "ein Fremder nicht");
        assertFalse(buch.beleb(verfallen, null), "und niemand schon gar nicht");
        assertEquals(Zustand.VERFALLEN,
                lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow().zustand());

        assertTrue(buch.beleb(verfallen, anna), "die BESITZERIN schon");
        assertEquals(Zustand.AKTIV,
                lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow().zustand());
    }

    @Test
    @DisplayName("Eine AKTIVE Stadt wird nicht belebt - und nicht geschrieben")
    void aktiveStadtWirdNichtBelebt() {
        Parzelle stadt = gegruendet();

        assertFalse(buch.beleb(stadt, anna));
    }

    @Test
    @DisplayName("OHNE Grundstuecksablage gilt jede Stadt als BEWOHNT - keine Raeumung")
    void ohneAblageGiltJedeStadtAlsBewohnt() {
        // DIE UMGEKEHRTE RICHTUNG als bei der Auflage, und es ist dieselbe Lesart: Eine
        // Wirtschaftsregel, die ausfaellt, darf nicht schaden. Bei der Auflage hiesse
        // "niemand ist Buerger" eine Vollsperre; hier hiesse es, dass der taegliche
        // Lauf JEDE unbesuchte Stadt abraeumt, obwohl in der Haelfte Leute wohnen.
        //
        // Das Buch hier hat keine Ablage - gibGrundstuecke wurde nie gerufen, genau wie
        // nach einem Ausfall beim Start.
        Parzelle stadt = gegruendet();
        lager.besuchSetzen(stadt.id(), LocalDate.of(2020, 1, 1));
        Parzelle alt = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();

        assertEquals(Verfallsregel.Stand.AKTIV, buch.verfallsstand(alt,
                LocalDate.of(2026, 11, 17), java.time.ZoneId.of("Europe/Berlin")),
                "laengst faellig - und trotzdem aktiv, weil niemand sagen kann, ob dort"
                        + " jemand wohnt");
    }

    @Test
    @DisplayName("MIT Ablage und ohne Buerger ist dieselbe Stadt FAELLIG")
    void mitAblageIstSieFaellig() {
        // Die Gegenprobe zu der darueber: Ohne sie waere jene gruen, auch wenn
        // verfallsstand immer AKTIV gaebe.
        Parzelle stadt = gegruendet();
        buch.gibGrundstuecke(new SpeicherGrundstuecke());
        lager.besuchSetzen(stadt.id(), LocalDate.of(2020, 1, 1));
        Parzelle alt = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();

        assertEquals(Verfallsregel.Stand.FAELLIG, buch.verfallsstand(alt,
                LocalDate.of(2026, 11, 17), java.time.ZoneId.of("Europe/Berlin")));
    }

    @Test
    @DisplayName("Abheben prueft den Stand VOR dem Abbuchen - und nicht erst danach")
    void abhebenPrueftVorher() {
        // FUND DER EINSPRITZUNGSRUNDE (F3): Nimmt man die Vorpruefung heraus, bleibt
        // alles gruen - denn dahinter liegt ein ZWEITER Riegel, und abbuchen antwortet
        // mit demselben Bescheid. Zwei Pruefungen fuer denselben Fall machen sich
        // gegenseitig unpruefbar.
        //
        // BEOBACHTBAR WIRD DER UNTERSCHIED MIT EINER KASSE, DIE BEIM ABBUCHEN WIRFT:
        // Mit der Vorpruefung kommt ZU_WENIG, weil abbuchen nie gerufen wird; ohne sie
        // kommt GESTOERT. Damit ist die Reihenfolge das Gepruefte und nicht der
        // Bescheid, den beide Riegel teilen.
        //
        // Der zweite Riegel bleibt trotzdem: Er ist der Fang gegen die Lage, in der
        // sich der Stand ZWISCHEN Lesen und Abbuchen aendert - ein Grundstueckskauf
        // zahlt ein, ein zweiter Befehl hebt ab.
        WerfendeKasse werfend = new WerfendeKasse();
        Freistadtbuch eigenes = new Freistadtbuch(Feldgrenze.freistaedte(), lager, kasse,
                new Stadtkasse(werfend), WARPS, VORGABE);
        eigenes.gruende(WELT, anna, "kassenstadt", VIELE);
        Parzelle stadt = eigenes.staedte(WELT).get(0);
        werfend.stand = 500L;

        Freistadtbuch.Kassenentscheid zuViel = eigenes.hebeAb(WELT, anna,
                mitteX(stadt), mitteZ(stadt), 600L);

        assertEquals(Freistadtbuch.Kassenbescheid.ZU_WENIG, zuViel.bescheid(),
                "die Vorpruefung antwortet, OHNE die Kasse anzufassen - ohne sie waere"
                        + " hier GESTOERT");
        assertEquals(0, werfend.versuche, "und abbuchen wurde nicht einmal versucht");
    }

    @Test
    @DisplayName("Die Erstattung rechnet vom EINGESETZTEN Preis, nicht vom heutigen")
    void erstattungVomEingesetztenPreis() {
        // FUND DER EINSPRITZUNGSRUNDE (F33): Alle Proben gruendeten mit dem
        // Vorgabepreis und rechneten gegen denselben - der Unterschied zwischen
        // "Preis der Zeile" und "Preis aus der Datei" war nie zu sehen. Das ist die
        // Lehre, dass sich A und B NUR in der geprueften Sache unterscheiden duerfen.
        //
        // IM SPIEL: Ein Betreiber senkt den Gruendungspreis von 400.000 auf 100.000.
        // Wer vorher gezahlt hat, bekaeme dann 50.000 statt 200.000 - und ein Jahr
        // Bauzeit waere die Haelfte des Einsatzes wert.
        Parzelle stadt = gegruendet();
        assertEquals(PREIS, stadt.preis(), "gegruendet zum alten Preis");

        Freistadtbuch billiger = new Freistadtbuch(Feldgrenze.freistaedte(), lager,
                kasse, stadtkasse, WARPS,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitFreistaedten(
                        Freistadtwerte.VORGABE.mitGruendungspreis(100_000L))));

        assertEquals(PREIS / 2, billiger.erstattungFuer(stadt),
                "die Haelfte von 400.000 und nicht von 100.000 - die Zeile traegt"
                        + " ihren Preis selbst");
    }

    // ------------------------------------- Der Besuchsstempel [Teil E4]

    @Test
    @DisplayName("Der Stempel schreibt nur, wenn der Tag sich AENDERT")
    void stempelSchreibtNurBeiAenderung() {
        // DIE WAHRHEIT HINTER DEM BILLIGEN VORTEST DES LAUFS. Jener merkt sich im
        // Arbeitsspeicher, welche Stadt heute schon gestempelt ist, und ueberlebt
        // keinen Neustart. Diese Zeile ueberlebt ihn: Nach einem Neustart mitten am Tag
        // wird nicht ein zweites Mal geschrieben.
        //
        // GEZAEHLT WIRD MIT EINER ATTRAPPE, nicht mit dem Rueckgabewert allein: Der
        // koennte true sagen und nichts schreiben.
        StempelLager mitschrift = new StempelLager();
        Freistadtbuch eigenes = new Freistadtbuch(Feldgrenze.freistaedte(), mitschrift,
                kasse, stadtkasse, WARPS, VORGABE);
        eigenes.gruende(WELT, anna, "stempelstadt", VIELE);
        Parzelle stadt = eigenes.staedte(WELT).get(0);
        LocalDate heute = LocalDate.of(2026, 11, 17);

        assertTrue(eigenes.stempleBesuch(stadt, heute), "der erste Stempel des Tages");
        assertEquals(1, mitschrift.gesetzt.size(), mitschrift.gesetzt.toString());
        assertEquals(heute, mitschrift.gesetzt.get(0));

        // DIESELBE Zeile, frisch gelesen - jetzt traegt sie den Tag schon.
        Parzelle danach = mitschrift.finde(WELT, stadt.x(), stadt.z()).orElseThrow();
        assertFalse(eigenes.stempleBesuch(danach, heute),
                "derselbe Tag wird nicht zweimal geschrieben");
        assertEquals(1, mitschrift.gesetzt.size(),
                "und zwar WIRKLICH nicht: " + mitschrift.gesetzt);

        // Der naechste Tag dagegen schon.
        assertTrue(eigenes.stempleBesuch(danach, heute.plusDays(1)));
        assertEquals(2, mitschrift.gesetzt.size());
    }

    @Test
    @DisplayName("Ein Stempel von MORGEN wird ueberschrieben, nicht geschont")
    void stempelAusDerZukunftWirdGeheilt() {
        // ANDERS ALS BEI DER TAGESSERIE: Dort haelt ein kuenftiger Tag die Zahlung an,
        // weil eine verstellte Uhr sonst Geld verschenkte. Hier gibt es nichts zu
        // verschenken, und der Besuch findet JETZT statt - ein Stempel von morgen ist
        // ein kaputter Wert, und dieser Zweig heilt ihn. Ohne ihn bliebe er stehen,
        // bis der Kalender ihn einholt.
        Parzelle stadt = gegruendet();
        LocalDate heute = LocalDate.of(2026, 11, 17);
        lager.besuchSetzen(stadt.id(), heute.plusDays(30));
        Parzelle verstellt = lager.finde(WELT, stadt.x(), stadt.z()).orElseThrow();

        assertTrue(buch.stempleBesuch(verstellt, heute));
        assertEquals(heute, lager.finde(WELT, stadt.x(), stadt.z())
                .orElseThrow().besucht());
    }

    @Test
    @DisplayName("Besucht wird eine Freistadt - und ohne Tag gibt es keinen Stempel")
    void stempelBrauchtStadtUndTag() {
        Parzelle stadt = gegruendet();

        assertThrows(IllegalArgumentException.class,
                () -> buch.stempleBesuch(null, LocalDate.of(2026, 11, 17)));
        assertThrows(IllegalArgumentException.class,
                () -> buch.stempleBesuch(stadt, null),
                "ohne Tag: Wer stempelt, hat heute ueber Tagesgrenze.tagVon bestimmt -"
                        + " dieses Buch kennt keine Uhr und keine Zone");
    }

    @Test
    @DisplayName("stadtAn gibt KEINE gewoehnliche Parzelle zurueck - auch nicht im Raster")
    void stadtAnGibtNurStaedte() {
        // FUND VOM 17.11.2026, und er traegt zwei Riegel auf einmal: Das Lager filtert
        // absichtlich nicht nach Art, und Marktstadt und Freistaedte teilen es. Die
        // Feldnummern der zwei Geometrien ueberschneiden sich - Block 150/150 der
        // Marktstadt liegt in Stadtfeld 0/0. Ohne diesen Filter bekam jeder Aufrufer
        // dort eine gewoehnliche Parzelle und lief anschliessend in auflage(), die
        // dafuer wirft.
        //
        // DIE PARZELLE LIEGT AUF FELD 0/0 der Stadtfeldrechnung, und zwar in der
        // Marktstadtwelt - genau die Lage, die der Fund beschreibt.
        lager.anlegen("freimarken:marktstadt", 0, 0, anna, 5_000L);

        assertTrue(buch.stadtAn("freimarken:marktstadt", 150, 150).isEmpty(),
                "eine Rasterparzelle ist keine Stadt - und diese Methode heisst"
                        + " stadtAn");
    }

    @Test
    @DisplayName("Eine Auflage haben nur Freistaedte - die Frage selbst wirft")
    void auflageNurFuerStaedte() {
        assertThrows(IllegalArgumentException.class, () -> buch.auflage(null));

        Parzelle parzelle = lager.anlegen("freimarken:marktstadt", 0, 0, anna, 0L);
        assertThrows(IllegalArgumentException.class, () -> buch.auflage(parzelle));
    }

}
