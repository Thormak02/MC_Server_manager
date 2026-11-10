package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Shopweg;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.Shopwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Warenprobe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /shop freigeben} - der SCHLUESSEL zu einer fremden Shopkiste.
 *
 * <p>Die wichtigste Probe hier ist {@link #freigegebenerHaengtKeinZweitesSchildAn}: Sie
 * haelt zusammen mit {@code KistenshopTest}, dass die Freigabe den dritten Weg NICHT
 * oeffnet. Waere er offen, stuende das Loch vom 08.11.2026 fuer genau die Leute wieder
 * auf, denen man gerade Vertrauen geschenkt hat - und es faellt nicht auf: Der fremde
 * Shop verkauft weiter, der Besitzer sieht nur, dass seine Kiste leerer wird.
 *
 * <p><b>Der Shop im Blick kommt aus einer Naht</b> ({@link ShopBefehle.Blickfang}). Ohne
 * sie waere dieser Zweig nicht pruefbar: Der Spielerstellvertreter beantwortet fuenfzehn
 * Methoden und wirft bei jeder anderen.
 */
@DisplayName("Befehl /shop freigeben")
class ShopBefehlTest {

    private static final String WELT = "freimarken:altfurt";

    private static final UUID ANNA = Spielerprobe.kennungAus(1);
    private static final UUID BERT = Spielerprobe.kennungAus(2);
    private static final UUID CORA = Spielerprobe.kennungAus(3);

    /**
     * Zwei Laeden je Seite - die Probe setzt ihre Zahl SELBST.
     *
     * <p>Nicht die Vorgabe (zehn): Mit zehn je Seite und drei Laeden gaebe es nie eine
     * zweite Seite, und der Umbruch - der eigentliche Gegenstand dieser Proben - waere
     * ungeprueft. Ausserdem braeche jede spaetere Aenderung der Vorgabe sonst ein
     * halbes Dutzend Proben, die mit ihr nichts zu tun haben.
     */
    private static final int ZEILEN_JE_SEITE = 2;

    private final SpeicherShops shops = new SpeicherShops();

    private World altfurt;

    /** Welches Schild der Spieler ansieht - von der Probe gestellt. */
    private Block imBlick;

    private final Map<String, UUID> bekannt = new HashMap<>();
    private final Map<UUID, String> schreibweise = new HashMap<>();

    private final Weltgriff griff = new Weltgriff() {
        @Override
        public int unten() {
            return -64;
        }

        @Override
        public int oben(int x, int z) {
            return 64;
        }

        @Override
        public Material bei(int x, int y, int z) {
            return Material.OAK_SIGN;
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
        }
    };

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        altfurt = Serverprobe.welt(WELT, griff);
        imBlick = altfurt.getBlockAt(60, 71, 6);
        bekannt.clear();
        bekannt.put("Anna", ANNA);
        bekannt.put("Bert", BERT);
        bekannt.put("Cora", CORA);
        schreibweise.clear();
        schreibweise.put(ANNA, "Anna");
        schreibweise.put(BERT, "Bert");
        schreibweise.put(CORA, "Cora");
    }

    /** Ein Shop von Anna am Schild 60/71/6, Kiste daneben. */
    private Kistenshop annasShop(Set<UUID> freigegeben) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, 16, 100L, 80L,
                new Warenprobe("Stein", "inhalt-von-stein"), freigegeben);
    }

    private Befehlsprobe probe() {
        return probe(shops);
    }

    private Befehlsprobe probe(Shopablage ablage) {
        ShopBefehle befehle = new ShopBefehle(ablage, Sprachprobe.spracheAusDemJar(),
                (wer, reichweite) -> imBlick,
                bekannt::get,
                kennung -> schreibweise.getOrDefault(kennung, "unbekannt"),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitHandel(
                        new Shopwerte(0, 0, 0, "Bund", ZEILEN_JE_SEITE))),
                Logger.getLogger("Probe"));
        return new Befehlsprobe(List.of(befehle.shop()));
    }

    /** Ein Shop von Anna an der Stelle x - das Schild bei x/71/6, die Kiste daneben. */
    private Kistenshop annasShopBei(int x, String ware) {
        return new Kistenshop(WELT, x, 71, 6, x + 1, 71, 6, ANNA, 16, 100L, 80L,
                new Warenprobe(ware, "inhalt-von-" + ware), Set.of());
    }

    private Spielerprobe anna() {
        return new Spielerprobe("Anna", ANNA, ShopBefehle.RECHT).inWelt(altfurt);
    }

    /** Der Shop, wie er JETZT in der Ablage steht. */
    private Kistenshop gespeichert() {
        return shops.anDemSchild(WELT, 60, 71, 6);
    }

    @Test
    @DisplayName("Der Besitzer gibt jemanden frei, und der darf danach an die Kiste")
    void freigebenOeffnetDieKiste() throws CommandSyntaxException {
        shops.schreibe(annasShop(Set.of()));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop freigeben Bert", anna));

        assertTrue(gespeichert().istFreigegeben(BERT), "er steht in der Liste");
        assertTrue(gespeichert().offenFuer(BERT, Shopweg.OEFFNEN, false, false),
                "und die Kiste ist fuer ihn offen - darum geht es");
        assertTrue(anna.chatBild().get(0).contains("Bert"), anna.chatBild().toString());
    }

    @Test
    @DisplayName("Die Zusage nennt, was die Freigabe NICHT erlaubt")
    void zusageNenntDieGrenze() throws CommandSyntaxException {
        // Sonst muesste der Besitzer raten, wie weit er gerade Vertrauen verschenkt hat.
        // Dass das zweite Schild verwehrt bleibt, ist keine Feinheit - es ist der Weg,
        // auf dem jemand fremde Ware auf SEIN Konto verkaufen koennte.
        shops.schreibe(annasShop(Set.of()));
        Spielerprobe anna = anna();

        probe().tippe("shop freigeben Bert", anna);

        String zeile = anna.chatBild().get(0);
        assertTrue(zeile.contains("Schild"),
                "die Zusage muss das zweite Schild ausdruecklich ausnehmen: " + zeile);
    }

    @Test
    @DisplayName("Ein Freigegebener haengt KEIN zweites Schild an - das Loch bleibt zu")
    void freigegebenerHaengtKeinZweitesSchildAn() throws CommandSyntaxException {
        // DIE WICHTIGSTE PROBE DIESES BEFEHLS, und sie prueft die FOLGE der Freigabe und
        // nicht nur ihren Eintrag: Nach dem Befehl muss der dritte Weg immer noch
        // verschlossen sein.
        shops.schreibe(annasShop(Set.of()));

        probe().tippe("shop freigeben Bert", anna());

        Kistenshop nachher = gespeichert();
        assertTrue(nachher.offenFuer(BERT, Shopweg.OEFFNEN, false, false));
        assertTrue(nachher.offenFuer(BERT, Shopweg.ABBAUEN, false, false));
        assertFalse(nachher.offenFuer(BERT, Shopweg.ZWEITES_SCHILD, false, false),
                "derselbe Spieler, derselbe Shop - NUR der Weg ist anders. Waere er"
                        + " offen, verkaufte Bert Annas Ware auf SEIN Konto, und sie"
                        + " saehe nur, dass ihre Kiste leerer wird.");
    }

    @Test
    @DisplayName("Sperren nimmt die Freigabe zurueck")
    void sperrenNimmtZurueck() throws CommandSyntaxException {
        shops.schreibe(annasShop(Set.of(BERT)));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop sperren Bert", anna));

        assertFalse(gespeichert().istFreigegeben(BERT));
        assertFalse(gespeichert().offenFuer(BERT, Shopweg.OEFFNEN, false, false),
                "und die Kiste ist wieder zu");
    }

    @Test
    @DisplayName("Mehrere Freigaben stehen nebeneinander")
    void mehrereFreigaben() throws CommandSyntaxException {
        // Die Entscheidung vom 09.11.2026: eine LISTE, kein einzelner Name. Ein
        // gemeinsamer Laden von drei Freunden geht nur so.
        shops.schreibe(annasShop(Set.of()));

        probe().tippe("shop freigeben Bert", anna());
        probe().tippe("shop freigeben Cora", anna());

        assertEquals(2, gespeichert().freigegeben().size());
        assertTrue(gespeichert().istFreigegeben(BERT));
        assertTrue(gespeichert().istFreigegeben(CORA));
    }

    @Test
    @DisplayName("Sperren trifft NUR den Genannten")
    void sperrenTrifftNurDenGenannten() throws CommandSyntaxException {
        shops.schreibe(annasShop(Set.of(BERT, CORA)));

        probe().tippe("shop sperren Bert", anna());

        assertFalse(gespeichert().istFreigegeben(BERT));
        assertTrue(gespeichert().istFreigegeben(CORA),
                "die andere Freigabe bleibt - sonst sperrte ein Befehl alle aus");
    }

    @Test
    @DisplayName("Zweimal freigeben sagt es und aendert nichts")
    void zweimalFreigebenSagtEs() throws CommandSyntaxException {
        // Keine stille Wiederholung: Wer zweimal freigibt, soll wissen, dass beim ersten
        // Mal schon alles stimmte - sonst sucht er den Fehler.
        shops.schreibe(annasShop(Set.of(BERT)));
        Spielerprobe anna = anna();

        assertEquals(0, probe().tippe("shop freigeben Bert", anna));

        assertEquals(1, gespeichert().freigegeben().size());
        assertTrue(anna.chatBild().get(0).contains("schon"), anna.chatBild().toString());
    }

    @Test
    @DisplayName("Jemanden sperren, der gar nicht frei war, sagt es")
    void sperrenOhneFreigabeSagtEs() throws CommandSyntaxException {
        shops.schreibe(annasShop(Set.of()));
        Spielerprobe anna = anna();

        assertEquals(0, probe().tippe("shop sperren Bert", anna));

        assertTrue(anna.chatBild().get(0).contains("ohnehin nicht"),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("Ein FREMDER Shop laesst sich nicht freigeben")
    void fremderShopGehtNicht() throws CommandSyntaxException {
        // Freigeben darf NUR der Besitzer. Waere der Schluessel selbst weitergebbar,
        // wuesste nach zwei Schritten niemand mehr, wer eigentlich Zutritt hat.
        shops.schreibe(annasShop(Set.of()));
        Spielerprobe bert = new Spielerprobe("Bert", BERT, ShopBefehle.RECHT)
                .inWelt(altfurt);

        assertEquals(0, probe().tippe("shop freigeben Cora", bert));

        assertTrue(gespeichert().freigegeben().isEmpty(), "nichts geaendert");
        assertTrue(bert.chatBild().get(0).contains("gehoert dir nicht"),
                bert.chatBild().toString());
    }

    @Test
    @DisplayName("Auch ein schon FREIGEGEBENER darf nicht weiterverschenken")
    void freigegebenerDarfNichtWeitergeben() throws CommandSyntaxException {
        // Der Schluessel ist nicht selbst weitergebbar. Sonst waere die Liste nach zwei
        // Schritten eine, die der Besitzer nie eingetragen hat.
        shops.schreibe(annasShop(Set.of(BERT)));
        Spielerprobe bert = new Spielerprobe("Bert", BERT, ShopBefehle.RECHT)
                .inWelt(altfurt);

        assertEquals(0, probe().tippe("shop freigeben Cora", bert));

        assertFalse(gespeichert().istFreigegeben(CORA));
        assertTrue(bert.chatBild().get(0).contains("gehoert dir nicht"),
                bert.chatBild().toString());
    }

    @Test
    @DisplayName("Ein BUNDSHOP laesst sich nicht freigeben")
    void bundshopGehtNicht() throws CommandSyntaxException {
        // Er gehoert niemandem, also kann ihn auch niemand freigeben. Wer an ihn darf,
        // entscheidet allein freimarken.shop.bund.
        shops.schreibe(new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 16, 100L, 80L,
                new Warenprobe("Stein", "inhalt-von-stein"), Set.of()));
        Spielerprobe anna = anna();

        assertEquals(0, probe().tippe("shop freigeben Bert", anna));

        assertTrue(anna.chatBild().get(0).contains("Bundshop"),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("Sich selbst freizugeben sagt es und aendert nichts")
    void selbstFreigebenSagtEs() throws CommandSyntaxException {
        // Eine stille Annahme waere schlechter: Anna saehe eine Bestaetigung und
        // glaubte, etwas eingerichtet zu haben.
        shops.schreibe(annasShop(Set.of()));
        Spielerprobe anna = anna();

        assertEquals(0, probe().tippe("shop freigeben Anna", anna));

        assertTrue(gespeichert().freigegeben().isEmpty());
        assertTrue(anna.chatBild().get(0).contains("ohnehin"), anna.chatBild().toString());
    }

    @Test
    @DisplayName("Ohne Schild im Blick nennt die Absage die Reichweite")
    void ohneSchildNenntDieReichweite() throws CommandSyntaxException {
        imBlick = null;
        Spielerprobe anna = anna();

        assertEquals(0, probe().tippe("shop freigeben Bert", anna));

        assertTrue(anna.chatBild().get(0).contains("5 Bloecke"),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("Ein Block ohne Shop ist wie kein Schild")
    void blockOhneShop() throws CommandSyntaxException {
        // Kein eigener Satz: Fuer den Spieler ist "da ist kein Shop" und "du schaust
        // ins Leere" dieselbe Lage - er muss sich vor sein Schild stellen.
        Spielerprobe anna = anna();

        assertEquals(0, probe().tippe("shop freigeben Bert", anna));

        assertTrue(anna.chatBild().get(0).contains("kein Shopschild"),
                anna.chatBild().toString());
    }

    @Test
    @DisplayName("Ein unbekannter Name ist eine Absage und traegt nichts ein")
    void unbekannterNameWirdAbgesagt() throws CommandSyntaxException {
        // Sonst stuende eine Freigabe fuer einen Spieler in der Liste, den es nicht
        // gibt - fuer immer, und niemand koennte sie je einloesen.
        shops.schreibe(annasShop(Set.of()));
        Spielerprobe anna = anna();

        assertEquals(0, probe().tippe("shop freigeben berrt", anna));

        assertTrue(gespeichert().freigegeben().isEmpty());
        assertTrue(anna.chatBild().get(0).contains("berrt"), anna.chatBild().toString());
    }

    @Test
    @DisplayName("Die Liste zeigt die Freigegebenen, nach Namen geordnet")
    void listeZeigtDieFreigegebenen() throws CommandSyntaxException {
        shops.schreibe(annasShop(Set.of(CORA, BERT)));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop freigaben", anna));

        List<String> gelesen = anna.chatBild();
        assertEquals(3, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("2"), gelesen.get(0));
        assertTrue(gelesen.get(1).contains("Bert"),
                "nach NAMEN geordnet, nicht nach Kennung: " + gelesen);
        assertTrue(gelesen.get(2).contains("Cora"), gelesen.toString());
    }

    @Test
    @DisplayName("Eine leere Liste sagt NIEMAND und zeigt keinen Kopf")
    void leereListeSagtNiemand() throws CommandSyntaxException {
        // "niemand" ist hier genau die Auskunft, auf die es ankommt - wer nur eine
        // Ueberschrift ohne Zeilen saehe, hielte es fuer einen Fehler.
        shops.schreibe(annasShop(Set.of()));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop freigaben", anna));

        List<String> gelesen = anna.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("niemand"), gelesen.get(0));
    }

    @Test
    @DisplayName("Eine gestoerte Ablage aendert NICHTS und sagt es")
    void stoerungAendertNichts() throws CommandSyntaxException {
        // Beim Sperren ist das die wichtigere Haelfte: Wer glaubt, jemanden ausgesperrt
        // zu haben, und der kommt weiter hinein, merkt es womoeglich nie.
        Shopablage gestoert = new Shopablage() {
            @Override
            public Kistenshop anDemSchild(String welt, int x, int y, int z) {
                return annasShop(Set.of(BERT));
            }

            @Override
            public List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
                return List.of();
            }

            @Override
            public List<Kistenshop> vonSpieler(UUID besitzer) {
                return List.of();
            }

            @Override
            public List<Kistenshop> alle() {
                return List.of();
            }

            @Override
            public void schreibe(Kistenshop shop) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public boolean loesche(String welt, int x, int y, int z) {
                return false;
            }
        };
        Spielerprobe anna = anna();

        assertEquals(0, probe(gestoert).tippe("shop sperren Bert", anna));

        String zeile = anna.chatBild().get(0);
        assertTrue(zeile.contains("NICHTS geaendert"),
                "das muss ausdruecklich dastehen: " + zeile);
    }

    @Test
    @DisplayName("Ohne Recht ist der Befehl unsichtbar")
    void ohneRechtUnsichtbar() {
        shops.schreibe(annasShop(Set.of()));
        Spielerprobe ohne = new Spielerprobe("Gast", Spielerprobe.kennungAus(8))
                .inWelt(altfurt);

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("shop freigeben Bert", ohne));
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("shop freigaben", ohne));
        // Auch die Liste: Das requires sitzt an der WURZEL und gilt damit fuer jeden
        // Zweig - aber eine Probe, die nur zwei von vier tippt, sagt das nicht.
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippe("shop liste", ohne));
    }

    @Test
    @DisplayName("Auf der Konsole gibt es den Befehl nicht - sie sieht kein Schild an")
    void konsoleSiehtKeinSchild() {
        // Als requires und nicht als gelesene Absage, anders als beim Blockbuch: Dort
        // hat die Konsole einen sinnvollen zweiten Weg (die Abfrage nach Spieler), hier
        // gibt es keinen. Wer eine fremde Freigabe setzen will, aendert die Zeile in der
        // Datenbank.
        shops.schreibe(annasShop(Set.of()));

        assertThrows(CommandSyntaxException.class,
                () -> probe().tippeAufDerKonsole("shop freigaben"));
        // Und die Liste ebenso, aber aus einem ANDEREN Grund: Sie sieht kein Schild an,
        // sie fragt nach den eigenen Laeden - und die Konsole hat keine. Ein Betreiber,
        // der die Laeden eines Spielers sehen will, fragt die Datenbank.
        assertThrows(CommandSyntaxException.class,
                () -> probe().tippeAufDerKonsole("shop liste"));
    }

    @Test
    @DisplayName("Die Freigabe aendert nichts am Besitz")
    void freigabeAendertNichtDenBesitz() throws CommandSyntaxException {
        // Freigeben verschenkt den Schluessel, nicht den Laden.
        shops.schreibe(annasShop(Set.of()));

        probe().tippe("shop freigeben Bert", anna());

        assertEquals(ANNA, gespeichert().besitzer());
        assertTrue(gespeichert().gehoert(ANNA));
        assertFalse(gespeichert().gehoert(BERT));
    }

    // --- /shop liste [seite] [10.11.2026] -------------------------------------

    @Test
    @DisplayName("Ohne eigenen Laden sagt die Liste es - und nennt den Weg zum ersten")
    void leereShoplisteSagtEs() throws CommandSyntaxException {
        // Und zwar OHNE Kopf: Wer eine Ueberschrift ohne Zeilen liest, haelt es fuer
        // einen Fehler. Dieselbe Entscheidung wie bei /shop freigaben.
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop liste", anna));

        List<String> gelesen = anna.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("noch keinen"), gelesen.get(0));
    }

    @Test
    @DisplayName("Die erste Seite zeigt den Kopf, zwei Laeden und den Weiter-Hinweis")
    void ersteSeiteZeigtZweiUndWeiter() throws CommandSyntaxException {
        shops.schreibe(annasShopBei(60, "Stein"));
        shops.schreibe(annasShopBei(70, "Holz"));
        shops.schreibe(annasShopBei(80, "Erde"));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop liste", anna));

        List<String> gelesen = anna.chatBild();
        assertEquals(4, gelesen.size(), "Kopf, zwei Laeden, Weiter: " + gelesen);
        // BEFUND DER EINSPRITZUNG L6 vom 10.11.2026: Die beiden PREISE liessen sich
        // vertauschen, ohne dass eine Probe rot wurde - die Proben pruefen Warennamen,
        // und die Bildtafel setzt die Preisstuecke FERTIG ein. Darum stehen hier beide
        // Zahlen, und sie sind verschieden: 100 ist der Kaufpreis, 80 der Ankauf.
        assertTrue(gelesen.get(1).contains("Kauf 100"),
                "der KAUFpreis gehoert ins Kaufstueck: " + gelesen.get(1));
        assertTrue(gelesen.get(1).contains("Ankauf 80"),
                "und der Verkaufpreis ins Ankaufstueck: " + gelesen.get(1));
        assertTrue(gelesen.get(0).contains("3"), "die ANZAHL, nicht die Seitenzahl: "
                + gelesen.get(0));
        assertTrue(gelesen.get(0).contains("1") && gelesen.get(0).contains("2"),
                "Seite 1 von 2: " + gelesen.get(0));
        assertTrue(gelesen.get(1).contains("Stein"), gelesen.get(1));
        assertTrue(gelesen.get(2).contains("Holz"), gelesen.get(2));
        assertTrue(gelesen.get(3).contains("2"),
                "der Hinweis nennt die ZAHL der naechsten Seite: " + gelesen.get(3));
    }

    @Test
    @DisplayName("Die LETZTE Seite zeigt den Rest und KEINEN Weiter-Hinweis")
    void letzteSeiteOhneWeiter() throws CommandSyntaxException {
        // Ein Hinweis auf eine Seite, die es nicht gibt, schickt den Spieler in eine
        // Absage - und er glaubt dann, etwas verloren zu haben.
        shops.schreibe(annasShopBei(60, "Stein"));
        shops.schreibe(annasShopBei(70, "Holz"));
        shops.schreibe(annasShopBei(80, "Erde"));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop liste 2", anna));

        List<String> gelesen = anna.chatBild();
        assertEquals(2, gelesen.size(), "Kopf und ein Laden, kein Hinweis: " + gelesen);
        assertTrue(gelesen.get(1).contains("Erde"), gelesen.get(1));
    }

    @Test
    @DisplayName("Eine Seite, die es nicht gibt, nennt die Zahl der vorhandenen")
    void seiteDieEsNichtGibt() throws CommandSyntaxException {
        // Ohne diese Zahl schickt die Absage den Spieler ins Probieren, und bei dreissig
        // Laeden probiert er lange.
        shops.schreibe(annasShopBei(60, "Stein"));
        Spielerprobe anna = anna();

        assertEquals(0, probe().tippe("shop liste 9", anna));

        List<String> gelesen = anna.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("9"), gelesen.get(0));
        assertTrue(gelesen.get(0).contains("1"),
                "und wie viele es GIBT: " + gelesen.get(0));
    }

    @Test
    @DisplayName("Die Liste ist nach der STELLE geordnet - numerisch, nicht als Text")
    void listeIstNachDerStelleGeordnet() throws CommandSyntaxException {
        // Die Stellen fallen als Text und als Zahl auseinander: Text waere 100, 8, 9 -
        // Zahl ist 8, 9, 100. Mit aufsteigenden Stellen pruefte diese Probe nichts.
        //
        // Die Ordnung kommt aus der Ablage (Kistenshop.NACH_STELLE, vom geteilten
        // Vertrag gehalten); hier wird geprueft, dass der Zweig sie nicht zerstoert -
        // etwa durch ein eigenes Sortieren oder durch einen Schnitt VOR dem Ordnen.
        shops.schreibe(annasShopBei(100, "Hundert"));
        shops.schreibe(annasShopBei(8, "Acht"));
        shops.schreibe(annasShopBei(9, "Neun"));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop liste", anna));

        List<String> gelesen = anna.chatBild();
        assertTrue(gelesen.get(1).contains("Acht"), "8 zuerst: " + gelesen);
        assertTrue(gelesen.get(2).contains("Neun"), "dann 9: " + gelesen);
    }

    @Test
    @DisplayName("Fremde Laeden und ein BUNDSHOP stehen in keiner Liste")
    void nurDieEigenen() throws CommandSyntaxException {
        // Der Bundshop gehoert niemandem - also niemandes Liste. Er darf aber auch nicht
        // als fremder gezaehlt werden: Stuende er in der Anzahl, suchte der Spieler
        // einen Laden, den er nie gebaut hat.
        shops.schreibe(annasShopBei(60, "Meins"));
        shops.schreibe(new Kistenshop(WELT, 70, 71, 6, 71, 71, 6, BERT, 16, 100L, 80L,
                new Warenprobe("Berts", "inhalt-von-Berts"), Set.of()));
        shops.schreibe(new Kistenshop(WELT, 80, 71, 6, 81, 71, 6, null, 16, 100L, 80L,
                new Warenprobe("Bundes", "inhalt-von-Bundes"), Set.of()));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop liste", anna));

        List<String> gelesen = anna.chatBild();
        assertEquals(2, gelesen.size(), "Kopf und EIN Laden: " + gelesen);
        assertTrue(gelesen.get(0).contains("1"), "die Anzahl ist eins: " + gelesen);
        assertTrue(gelesen.get(1).contains("Meins"), gelesen.get(1));
    }

    @Test
    @DisplayName("Ein Laden, der NUR ankauft, zeigt keinen Kaufpreis")
    void nurAnkaufZeigtKeinenKaufpreis() throws CommandSyntaxException {
        // DIE VERDREHUNG, die am 08.11.2026 in Shoptext ein Fehler war: Die Satznamen
        // sind aus Kundensicht, die Methoden des Kerns aus Shopsicht - verkauftWare()
        // ist kaufpreis > 0. Vertauscht bekaeme ein Laden, der nur ankauft, "Kauf 0
        // Mark" zu lesen, und eine 0 als Preis ist genau das, was Shopschild als
        // PREIS_BEIDE_NULL abweist.
        //
        // Gefunden hat es damals eine Probe wie diese; die Bildtafel sah es nicht, denn
        // beide Wortstuecke sind fuer sich richtig.
        shops.schreibe(new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, 16, 0L, 80L,
                new Warenprobe("Stein", "inhalt-von-stein"), Set.of()));
        Spielerprobe anna = anna();

        assertEquals(1, probe().tippe("shop liste", anna));

        String zeile = anna.chatBild().get(1);
        assertTrue(zeile.contains("Ankauf"), "der Ankauf steht da: " + zeile);
        assertFalse(zeile.contains("Kauf 0"), "aber KEIN Kauf fuer 0: " + zeile);
        assertTrue(zeile.contains("80"), "und zwar mit dem Verkaufpreis: " + zeile);
    }

    @Test
    @DisplayName("Eine gestoerte Ablage sagt es - und NICHT, die Laeden seien weg")
    void gestoerteAblageSagtEsBeiDerListe() throws CommandSyntaxException {
        // Eine Stoerung, die wie "du hast keine Shops" aussieht, laesst den Spieler
        // glauben, seine Laeden seien verschwunden. Darum ein eigener Satz.
        //
        // Eine EIGENE stumme Ablage und nicht die von stoerungAendertNichts: Jene
        // laesst vonSpieler durch und wirft erst beim Schreiben - sie prueft den
        // anderen Zweig. Hier muss genau das Lesen scheitern.
        Shopablage gestoert = new Shopablage() {
            @Override
            public Kistenshop anDemSchild(String welt, int x, int y, int z) {
                return null;
            }

            @Override
            public List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
                return List.of();
            }

            @Override
            public List<Kistenshop> vonSpieler(UUID besitzer) {
                throw new IllegalStateException("Datenbank weg");
            }

            @Override
            public List<Kistenshop> alle() {
                return List.of();
            }

            @Override
            public void schreibe(Kistenshop shop) {
            }

            @Override
            public boolean loesche(String welt, int x, int y, int z) {
                return false;
            }
        };
        Spielerprobe anna = anna();

        assertEquals(0, probe(gestoert).tippe("shop liste", anna));

        List<String> gelesen = anna.chatBild();
        assertEquals(1, gelesen.size(), gelesen.toString());
        assertTrue(gelesen.get(0).contains("NICHT weg"), gelesen.get(0));
    }

    @Test
    @DisplayName("Ohne Pflichtangabe wird der Befehl nicht gebaut")
    void ohnePflichtangabeWirdAbgewiesen() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        ShopBefehle.Blickfang blick = (wer, reichweite) -> imBlick;
        ShopBefehle.Namenssuche suche = bekannt::get;
        ShopBefehle.Namensgeber geber = schreibweise::get;
        Logger log = Logger.getLogger("Probe");

        Wirtschaftsstand stand = new Wirtschaftsstand(Wirtschaftswerte.VORGABE);

        assertThrows(IllegalArgumentException.class,
                () -> new ShopBefehle(null, sprache, blick, suche, geber, stand, log));
        assertThrows(IllegalArgumentException.class,
                () -> new ShopBefehle(shops, null, blick, suche, geber, stand, log));
        assertThrows(IllegalArgumentException.class,
                () -> new ShopBefehle(shops, sprache, null, suche, geber, stand, log));
        assertThrows(IllegalArgumentException.class,
                () -> new ShopBefehle(shops, sprache, blick, null, geber, stand, log));
        assertThrows(IllegalArgumentException.class,
                () -> new ShopBefehle(shops, sprache, blick, suche, null, stand, log));
        assertThrows(IllegalArgumentException.class,
                () -> new ShopBefehle(shops, sprache, blick, suche, geber, null, log));
        assertThrows(IllegalArgumentException.class,
                () -> new ShopBefehle(shops, sprache, blick, suche, geber, stand, null));
    }
}
