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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                kasse, WARPS, VORGABE);
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
                kasse, WARPS, mitVorgabe);

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

}
