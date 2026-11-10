package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Shopschild;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.Shopwerte;
import de.freimarken.kern.Warenprobe;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Anlegen und Abbauen von Kistenshops - ohne Server.
 *
 * <p>Die Bukkit-Seite kommt ueber zwei Stellvertreter herein: ein {@link Warenleser}, der
 * sagt, was wo liegt, und ein {@link Zugriff}, der sagt, wer wo darf. Dadurch ist jeder
 * Bescheid, jede Reihenfolge und jede Absage hier pruefbar - und das ist der ganze Grund,
 * warum {@link Shopbuch} kein Bukkit kennt.
 *
 * <p><b>Die Reihenfolge der Absagen wird mit einer WERFENDEN Ablage geprueft und nicht
 * mit einem Zaehler.</b> Eine Zaehltafel liesse sich hochdrehen, und bei einer
 * Fluchtfolge waere sie blind. Eine Ablage, die bei jeder Frage wirft, macht die Zusage
 * hart: Kommt {@code SCHILD_FEHLERHAFT} heraus, wurde sie nicht gefragt - sonst stuende
 * dort {@code GESTOERT}.
 */
@DisplayName("Kistenshops anlegen und abbauen")
class ShopbuchTest {

    private static final String WELT = "freimarken:altfurt";
    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000a111");
    private static final UUID BERT = UUID.fromString("00000000-0000-0000-0000-00000000b222");
    private static final String BUNDWORT = "Bund";

    /** Ein gewoehnlicher Stapel Stein als Ware - derselbe in jeder Probe. */
    private static final Warenprobe STEIN = new Warenprobe("STONE", "ZWVpbi1zdGVpbg==");

    private Shopablage ablage;
    private Leser leser;
    private Wachmann wachmann;
    private Shopbuch buch;

    @BeforeEach
    void bauen() {
        ablage = new SpeicherShops();
        leser = new Leser();
        wachmann = new Wachmann();
        buch = new Shopbuch(ablage, leser, wachmann, wirtschaft,
                Logger.getAnonymousLogger());
    }

    // --- Das Anlegen gelingt --------------------------------------------------

    @Test
    @DisplayName("Ein gelesenes Schild wird ein Shop - mit allen Werten")
    void anlegenGelingt() {
        Shopbuch.Anlage anlage = anlege(ANNA, "", "16", "B 100 : S 80", "STONE");

        assertEquals(Shopbuch.Bescheid.ANGELEGT, anlage.bescheid());
        Kistenshop shop = anlage.shop();
        assertNotNull(shop);
        assertEquals(ANNA, shop.besitzer());
        assertEquals(16, shop.menge());
        assertEquals(100L, shop.kaufpreis());
        assertEquals(80L, shop.verkaufpreis());
        assertEquals(STEIN, shop.ware());
        // Und er steht WIRKLICH in der Ablage: Ein Bescheid allein ist eine Behauptung.
        assertEquals(shop, ablage.anDemSchild(WELT, 10, 64, 10));
    }

    @Test
    @DisplayName("Das Fragezeichen holt die Ware aus der Kiste")
    void fragezeichenLiestDieKiste() {
        // Der NORMALWEG seit der Entscheidung vom 08.11.2026: Verzauberte Ausruestung
        // passt auf keine Schildzeile, also legt man sie in die Kiste.
        leser.inDerKiste = new Warenprobe("+PICKAXE", "dmVyemF1YmVydA==");

        Shopbuch.Anlage anlage = anlege(ANNA, "", "1", "B 5000", "?");

        assertEquals(Shopbuch.Bescheid.ANGELEGT, anlage.bescheid());
        assertEquals("+PICKAXE", anlage.shop().ware().name());
        assertEquals("dmVyemF1YmVydA==", anlage.shop().ware().inhalt());
    }

    @Test
    @DisplayName("Ein Bundshop hat keinen Besitzer - und wird NICHT nach Zugriff gefragt")
    void bundshopGehoertNiemandem() {
        // Die Kiste eines Bundshops gehoert dem Staat, nicht dem Spieler, der das Schild
        // schreibt. Wer das Recht hat, legt ihn auch dort an, wo er selbst nicht bauen
        // darf - und genau das ist der Sinn des eigenen Rechts. Der Wachmann sagt hier
        // ueberall nein, und der Shop entsteht trotzdem.
        wachmann.erlaubt = false;

        Shopbuch.Anlage anlage = anlegeMitBund(ANNA, BUNDWORT, "64", "S 1", "STONE",
                true);

        assertEquals(Shopbuch.Bescheid.ANGELEGT, anlage.bescheid());
        assertTrue(anlage.shop().demBund());
        assertNull(anlage.shop().besitzer());
        assertTrue(wachmann.gefragt.isEmpty(), "ein Bundshop fragt den Zugriff gar nicht");
    }

    // --- Der ANKAUFSTAND des Bundes [10.11.2026] ------------------------------

    @Test
    @DisplayName("Das Bundwort kommt aus dem HALTER - eine Aenderung wirkt sofort")
    void bundwortKommtAusDemHalter() {
        // DER FUND VOM 10.11.2026. Das Wort kam vorher als Text in jeden Aufruf von
        // anlege, also aus dem Stand beim SERVERSTART - und weil ein /freimarken
        // neuladen absichtlich kein Objekt neu baut, wirkte eine Aenderung von
        // handel.bundshop-wort nie, waehrend der Befehl "uebernommen" meldete.
        //
        // Hier wird der Halter MITTEN IM LAUF getauscht, wie der Befehl es tut: Das alte
        // Wort macht danach keinen Bundshop mehr, das neue schon.
        wirtschaft.uebernimm(wirtschaft.werte().mitHandel(
                wirtschaft.werte().handel().mitWort("Staat")));

        Shopbuch.Anlage mitAltem = anlegeMitBund(ANNA, BUNDWORT, "5", "S 1", "STONE",
                true);
        assertEquals(Shopbuch.Bescheid.ANGELEGT, mitAltem.bescheid());
        assertEquals(ANNA, mitAltem.shop().besitzer(),
                "\"Bund\" ist jetzt ein gewoehnlicher Name in der ersten Zeile");

        Shopbuch.Anlage mitNeuem = buch.anlege(ANNA, WELT, 11, 65, 10, 11, 64, 10,
                zeilen("Staat", "5", "S 1", "STONE"), true, false);

        assertEquals(Shopbuch.Bescheid.ANGELEGT, mitNeuem.bescheid());
        assertTrue(mitNeuem.shop().demBund(), "das NEUE Wort wirkt ohne Neustart");
    }

    // --- Das Schild ist falsch: und die Ablage wird NICHT gefragt -------------

    @Test
    @DisplayName("Ein falsches Schild kostet keine Abfrage - die Ablage wirft und schweigt")
    void falschesSchildFragtDieAblageNicht() {
        // Die harte Form der Reihenfolgezusage: Die Ablage wirft bei JEDER Frage. Kommt
        // SCHILD_FEHLERHAFT heraus, wurde sie nicht gefragt - sonst stuende GESTOERT da.
        buch = new Shopbuch(new StolperndeAblage(), leser, wachmann, wirtschaft,
                Logger.getAnonymousLogger());

        Shopbuch.Anlage anlage = anlege(ANNA, "", "keine zahl", "B 100", "STONE");

        assertEquals(Shopbuch.Bescheid.SCHILD_FEHLERHAFT, anlage.bescheid());
        assertEquals(Shopschild.Fehler.MENGE_KEINE_ZAHL, anlage.fehler());
        assertEquals(2, anlage.zeile(), "der Spieler soll lesen, WO er nachsehen muss");
    }

    @Test
    @DisplayName("Jeder Schildfehler kommt mit seiner Zeilennummer durch")
    void jederSchildfehlerKommtDurch() {
        // Nicht alle acht, aber je Zeile einer: Dass Shopschild sie richtig unterscheidet,
        // ist dort geprueft - hier geht es darum, dass das Buch Fehler UND Zeile
        // weitergibt und nicht bloss "geht nicht" sagt.
        assertEquals(3, anlege(ANNA, "", "16", "Hallo", "STONE").zeile());
        assertEquals(Shopschild.Fehler.PREIS_VERDREHT,
                anlege(ANNA, "", "16", "B 80 : S 100", "STONE").fehler());
        assertEquals(4, anlege(ANNA, "", "16", "B 100", "").zeile());
    }

    @Test
    @DisplayName("Ohne Bundrecht kein Bundshop - und auch das kostet keine Abfrage")
    void ohneBundrechtKeinBundshop() {
        buch = new Shopbuch(new StolperndeAblage(), leser, wachmann, wirtschaft,
                Logger.getAnonymousLogger());

        Shopbuch.Anlage anlage = anlegeMitBund(ANNA, BUNDWORT, "16", "B 100", "STONE",
                false);

        assertEquals(Shopbuch.Bescheid.KEIN_BUNDRECHT, anlage.bescheid());
    }

    // --- Die Welt sagt nein ---------------------------------------------------

    @Test
    @DisplayName("Ohne Kiste neben dem Schild entsteht kein Shop")
    void ohneKisteKeinShop() {
        leser.istKiste = false;

        assertEquals(Shopbuch.Bescheid.KEINE_KISTE,
                anlege(ANNA, "", "16", "B 100", "STONE").bescheid());
    }

    @Test
    @DisplayName("Gefragt wird nach der KISTENSTELLE und nicht nach dem Schild")
    void gefragtWirdNachDerKistenstelle() {
        // DIE LUECKE, DIE KEIN VORBILD SELBST SCHLIESST: Das Schild haengt auf eigenem
        // Grund, die Kiste steht einen Block weiter auf fremdem. Ohne diese Pruefung
        // verkaufte der Shop die Ware eines anderen.
        //
        // Geprueft wird am ORT, nicht an einer Zahl: Der Wachmann erlaubt die
        // Schildstelle und verbietet die Kistenstelle. Faellt die Pruefung auf die
        // falsche Stelle zurueck, entsteht der Shop - und die Probe ist rot.
        wachmann.verboten.add(stelle(11, 64, 10));

        Shopbuch.Anlage anlage = buch.anlege(ANNA, WELT, 10, 64, 10, 11, 64, 10,
                zeilen("", "16", "B 100", "STONE"), false, false);

        assertEquals(Shopbuch.Bescheid.FREMDE_KISTE, anlage.bescheid());
        assertEquals(List.of(stelle(11, 64, 10)), wachmann.gefragt,
                "gefragt wurde GENAU die Kistenstelle, und zwar nur sie");
    }

    @Test
    @DisplayName("Das Fragezeichen ohne eindeutige Ware ist WARE_UNKLAR")
    void fragezeichenOhneWare() {
        leser.inDerKiste = null;

        assertEquals(Shopbuch.Bescheid.WARE_UNKLAR,
                anlege(ANNA, "", "16", "B 100", "?").bescheid());
    }

    @Test
    @DisplayName("Ein getippter Gegenstand, den es nicht gibt, ist WARE_UNBEKANNT")
    void getippteWareUnbekannt() {
        // ZWEI verschiedene Bescheide fuer "keine Ware", und das ist der Grund, warum es
        // sie beide gibt: Der eine Spieler muss in die Kiste sehen, der andere auf sein
        // Schild. Eine gemeinsame Absage liesse beide beim Falschen suchen.
        leser.ausNamen = null;

        assertEquals(Shopbuch.Bescheid.WARE_UNBEKANNT,
                anlege(ANNA, "", "16", "B 100", "STEIN").bescheid());
    }

    @Test
    @DisplayName("An einem Schild mit Shop entsteht kein zweiter")
    void keinZweiterShopAmSelbenSchild() {
        anlege(ANNA, "", "16", "B 100", "STONE");

        Shopbuch.Anlage zweite = anlege(BERT, "", "32", "B 200", "STONE");

        assertEquals(Shopbuch.Bescheid.SCHON_EIN_SHOP, zweite.bescheid());
        assertEquals(ANNA, ablage.anDemSchild(WELT, 10, 64, 10).besitzer(),
                "der erste Shop bleibt unveraendert");
    }

    @Test
    @DisplayName("Eine stumme Ablage gibt GESTOERT und keinen halben Shop")
    void stummeAblageGibtGestoert() {
        buch = new Shopbuch(new StolperndeAblage(), leser, wachmann, wirtschaft,
                Logger.getAnonymousLogger());

        Shopbuch.Anlage anlage = anlege(ANNA, "", "16", "B 100", "STONE");

        assertEquals(Shopbuch.Bescheid.GESTOERT, anlage.bescheid());
        assertNull(anlage.shop(), "ein gestoerter Versuch hat keinen Shop");
    }

    // --- Das Abbauen ----------------------------------------------------------

    @Test
    @DisplayName("Abbauen loescht die Zeile und nennt den Shop, der weg ist")
    void abbauenLoescht() {
        anlege(ANNA, "", "16", "B 100", "STONE");

        assertEquals(Shopbuch.Bescheid.ABGEBAUT, buch.baueAb(WELT, 10, 64, 10));
        assertNull(ablage.anDemSchild(WELT, 10, 64, 10));
    }

    @Test
    @DisplayName("Abbauen, wo kein Shop war, ist KEIN Fehler")
    void abbauenOhneShop() {
        // Dieselbe Stelle wird von zwei Seiten gemeldet - vom Schild und von der Kiste -,
        // und die zweite Meldung soll nicht klagen. Darum KEIN_SHOP und nicht GESTOERT.
        assertEquals(Shopbuch.Bescheid.KEIN_SHOP, buch.baueAb(WELT, 10, 64, 10));
    }

    @Test
    @DisplayName("Zweimal abbauen gibt beim zweiten Mal KEIN_SHOP")
    void zweimalAbbauen() {
        anlege(ANNA, "", "16", "B 100", "STONE");

        assertEquals(Shopbuch.Bescheid.ABGEBAUT, buch.baueAb(WELT, 10, 64, 10));
        assertEquals(Shopbuch.Bescheid.KEIN_SHOP, buch.baueAb(WELT, 10, 64, 10));
    }

    @Test
    @DisplayName("Eine stumme Ablage macht aus dem Abbauen GESTOERT")
    void abbauenGestoert() {
        buch = new Shopbuch(new StolperndeAblage(), leser, wachmann, wirtschaft,
                Logger.getAnonymousLogger());

        assertEquals(Shopbuch.Bescheid.GESTOERT, buch.baueAb(WELT, 10, 64, 10));
    }

    // --- Die zwei Fragen fuer die Wache, und ihre GEGENSAETZLICHEN Ausfaelle ---

    @Test
    @DisplayName("anDemSchild gibt bei einer Stoerung NULL - der Klick bewirkt nichts")
    void anDemSchildGibtNullBeiStoerung() {
        buch = new Shopbuch(new StolperndeAblage(), leser, wachmann, wirtschaft,
                Logger.getAnonymousLogger());

        assertNull(buch.anDemSchild(WELT, 10, 64, 10));
    }

    @Test
    @DisplayName("istShopkiste gibt bei einer Stoerung TRUE - die Kiste wird geschuetzt")
    void istShopkisteGibtTrueBeiStoerung() {
        // DIE UMGEKEHRTE RICHTUNG, und sie ist die wichtigere von beiden: Hier heisst die
        // Antwort "schuetze diese Kiste". Ein stilles Nein waere eine Markthalle, aus der
        // jeder Trichter abzieht, solange die Datenbank schweigt - und eine leergezogene
        // Kiste bekommt niemand zurueck.
        //
        // Die zwei Proben zusammen sind der Punkt: Waeren beide gleich, waere eine von
        // ihnen falsch.
        buch = new Shopbuch(new StolperndeAblage(), leser, wachmann, wirtschaft,
                Logger.getAnonymousLogger());

        assertTrue(buch.istShopkiste(WELT, 11, 64, 10));
        assertTrue(buch.anDerKiste(WELT, 11, 64, 10).isEmpty(),
                "die LISTE bleibt leer - eine erfundene Zeile waere schlimmer als keine");
    }

    @Test
    @DisplayName("istShopkiste findet den Shop an seiner Kiste")
    void istShopkisteFindet() {
        anlege(ANNA, "", "16", "B 100", "STONE");

        assertTrue(buch.istShopkiste(WELT, 11, 64, 10));
        assertEquals(1, buch.anDerKiste(WELT, 11, 64, 10).size());
        assertTrue(buch.anDerKiste(WELT, 99, 64, 99).isEmpty());
    }

    // --- Das ZWEITE Schild an derselben Kiste [08.11.2026] --------------------

    @Test
    @DisplayName("An der Kiste eines anderen entsteht kein zweiter Shop")
    void keinZweitesSchildAnFremderKiste() {
        // DIE ANDERE HAELFTE DES KISTENSCHUTZES: Wer die Kiste nicht OEFFNEN kann,
        // haengt sonst einfach ein eigenes Schild daneben und verkauft die fremde
        // Ware auf sein Konto. Die Pruefung ueber den Zugriff sieht das nicht - sie
        // fragt den ORT, und draussen vor der Stadt gehoert kein Ort jemandem.
        //
        // Der Wachmann erlaubt hier ALLES, genau wie Schutzwache.darfHandeln
        // ausserhalb der Parzellenwelt. Waere die neue Pruefung nur eine zweite
        // Frage an ihn, waere diese Probe gruen und der Shop trotzdem zu kapern.
        anlege(ANNA, "", "16", "B 100", "STONE");

        assertEquals(Shopbuch.Bescheid.FREMDER_SHOP,
                zweitesSchild(BERT, "", "STONE", false, false).bescheid());
        assertEquals(1, ablage.vonSpieler(ANNA).size(),
                "und der Shop von Anna ist unveraendert einer");
    }

    @Test
    @DisplayName("Ein FREIGEGEBENER haengt kein zweites Schild an")
    void freigegebenerHaengtKeinZweitesSchildAn() {
        // Die Freigabe oeffnet die Kiste und erlaubt das Abbauen - das zweite Schild
        // nicht, und darum muss das Anlegen nach genau DIESEM Weg fragen. Fragte es
        // nach dem Oeffnen, stuende das Loch vom 08.11.2026 fuer genau die Leute
        // wieder offen, denen man gerade Vertrauen geschenkt hat.
        anlege(ANNA, "", "16", "B 100", "STONE");
        ablage.schreibe(ablage.anDemSchild(WELT, 10, 64, 10)
                .mitFreigaben(Set.of(BERT)));
        assertTrue(ablage.anDemSchild(WELT, 10, 64, 10).istFreigegeben(BERT),
                "Vorbedingung: Bert IST freigegeben - sonst prueft diese Probe nur,"
                        + " dass ein voelliger Fremder abgewiesen wird, und das tut"
                        + " keinZweitesSchildAnFremderKiste schon");

        assertEquals(Shopbuch.Bescheid.FREMDER_SHOP,
                zweitesSchild(BERT, "", "STONE", false, false).bescheid());
        assertEquals(1, ablage.vonSpieler(ANNA).size(),
                "und der Shop von Anna ist unveraendert einer");
    }

    @Test
    @DisplayName("An der EIGENEN Kiste entsteht ein zweiter Shop")
    void zweitesSchildAnEigenerKiste() {
        // Zwei Schilder an einer Kiste sind kein Fehler, sondern der Weg zu zwei
        // Mengen oder zwei Preisen derselben Ware. Verboten ist nur das FREMDE.
        anlege(ANNA, "", "16", "B 100", "STONE");

        assertEquals(Shopbuch.Bescheid.ANGELEGT,
                zweitesSchild(ANNA, "", "STONE", false, false).bescheid());
    }

    @Test
    @DisplayName("Das Teamrecht darf an die fremde Kiste - sonst bleibt ein"
            + " verlassener Shop fuer immer stehen")
    void teamrechtDarfAnDieFremdeKiste() {
        anlege(ANNA, "", "16", "B 100", "STONE");

        assertEquals(Shopbuch.Bescheid.ANGELEGT,
                zweitesSchild(BERT, "", "STONE", false, true).bescheid());
    }

    @Test
    @DisplayName("An der Kiste eines BUNDSHOPS braucht es das Bundrecht")
    void anBundshopkisteBrauchtDasBundrecht() {
        // Und zwar an demBund() und nicht am Besitzerfeld: Ein Bundshop gehoert
        // niemandem, gehoert() gibt dort fuer jeden false. Eine Frage nach dem
        // Besitz allein liesse hier NIEMANDEN mehr ein zweites Schild haengen -
        // auch den nicht, der den Bundshop gerade selbst gebaut hat.
        anlegeMitBund(ANNA, "Bund", "16", "B 100", "STONE", true);

        assertEquals(Shopbuch.Bescheid.FREMDER_SHOP,
                zweitesSchild(BERT, "", "STONE", false, false).bescheid());
        assertEquals(Shopbuch.Bescheid.ANGELEGT,
                zweitesSchild(BERT, "", "STONE", true, false).bescheid());
    }

    @Test
    @DisplayName("Die Kistenfrage kommt VOR der Warenpruefung")
    void kistenfrageVorDerWarenpruefung() {
        // Sonst liest das Fragezeichen die fremde Kiste, und bei einer unklaren
        // Ware bekaeme Bert "die Kiste sagt nicht, was du handeln willst" zu lesen -
        // eine Absage, die ihn an seinem Schild suchen laesst, waehrend das Problem
        // einen Block weiter haengt. Der Unterschied ist genau die Reihenfolge.
        anlege(ANNA, "", "16", "B 100", "STONE");
        leser.inDerKiste = null;

        assertEquals(Shopbuch.Bescheid.FREMDER_SHOP,
                zweitesSchild(BERT, "", "?", false, false).bescheid(),
                "der fremde Shop wird gemeldet, nicht die unklare Ware");
    }

    // --- Werkzeug -------------------------------------------------------------

    /**
     * Der Halter, aus dem das Buch sein Bundwort nimmt.
     *
     * <p>Veraenderlich, weil GENAU DAS der Fund vom 10.11.2026 war: Das Bundwort kam
     * vorher als Text in jeden Aufruf von {@code anlege} und war damit der Stand des
     * SERVERSTARTS - ein Neuladen wirkte nie.
     *
     * <p>Eine ANKAUFTAFEL stand hier einen halben Tag lang mit: Stein zu 20 Pfennigen,
     * weil das Buch damals jeden Ankaufstand gegen sie prueefte. Die Tafel ist gefallen -
     * der Preis steht auf dem Schild.
     */
    private Wirtschaftsstand wirtschaft = new Wirtschaftsstand(
            Wirtschaftswerte.VORGABE.mitHandel(Shopwerte.VORGABE.mitWort(BUNDWORT)));

    private Shopbuch.Anlage anlege(UUID wer, String erste, String menge, String preise,
            String ware) {
        return buch.anlege(wer, WELT, 10, 64, 10, 11, 64, 10,
                zeilen(erste, menge, preise, ware), false, false);
    }

    private Shopbuch.Anlage anlegeMitBund(UUID wer, String erste, String menge,
            String preise, String ware, boolean darfBund) {
        return buch.anlege(wer, WELT, 10, 64, 10, 11, 64, 10,
                zeilen(erste, menge, preise, ware), darfBund, false);
    }

    /**
     * Ein ZWEITES Schild an derselben Kiste - es sitzt UEBER ihr.
     *
     * <p>Die Kiste steht bei 11/64/10, das erste Schild daneben bei 10/64/10, dieses
     * bei 11/65/10. Beide sind Nachbarn der Kiste (ein Block Abstand auf einer
     * Achse), und genau darum ist der Fall ueberhaupt moeglich.
     */
    private Shopbuch.Anlage zweitesSchild(UUID wer, String erste, String ware,
            boolean darfBund, boolean darfUeberall) {
        return buch.anlege(wer, WELT, 11, 65, 10, 11, 64, 10,
                zeilen(erste, "16", "B 100", ware), darfBund, darfUeberall);
    }

    private static String[] zeilen(String a, String b, String c, String d) {
        return new String[] {a, b, c, d};
    }

    private static String stelle(int x, int y, int z) {
        return WELT + " " + x + "/" + y + "/" + z;
    }

    /** Ein Warenleser, dessen drei Antworten je Probe einstellbar sind. */
    private static final class Leser implements Shopbuch.Warenleser {

        private Warenprobe ausNamen = STEIN;
        private Warenprobe inDerKiste = STEIN;
        private boolean istKiste = true;

        @Override
        public Warenprobe ausNamen(String getippt) {
            return ausNamen;
        }

        @Override
        public Warenprobe ausKiste(String welt, int x, int y, int z) {
            return inDerKiste;
        }

        @Override
        public boolean taugtAlsShopkiste(String welt, int x, int y, int z) {
            return istKiste;
        }
    }

    /**
     * Ein Zugriff, der jede Frage AUFSCHREIBT - mit der Stelle, nicht als Zahl.
     *
     * <p>Eine Zahl sagte nur, dass gefragt wurde; der Ort sagt, WO. Genau daran haengt
     * die Zusage, dass nach der Kistenstelle gefragt wird und nicht nach der des
     * Schildes - und das ist eine Luecke der Vorbilder.
     */
    private static final class Wachmann implements Shopbuch.Zugriff {

        private final List<String> gefragt = new ArrayList<>();
        private final List<String> verboten = new ArrayList<>();
        private boolean erlaubt = true;

        @Override
        public boolean darf(UUID wer, String welt, int x, int y, int z) {
            String ort = welt + " " + x + "/" + y + "/" + z;
            gefragt.add(ort);
            return erlaubt && !verboten.contains(ort);
        }
    }

    /**
     * Eine Ablage, die bei JEDER Frage wirft.
     *
     * <p>Sie ist zweierlei auf einmal: die stumme Datenbank fuer die Stoerungsproben UND
     * die Reihenfolgezusage. Fuer die zweite ist sie staerker als jede Zaehltafel - wer
     * sie fragt, bekommt GESTOERT, und das ist nicht hochzudrehen und nicht blind.
     */
    private static final class StolperndeAblage implements Shopablage {

        private static IllegalStateException stumm() {
            return new IllegalStateException("Die Datenbank antwortet nicht");
        }

        @Override
        public Kistenshop anDemSchild(String welt, int x, int y, int z) {
            throw stumm();
        }

        @Override
        public List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
            throw stumm();
        }

        @Override
        public List<Kistenshop> vonSpieler(UUID besitzer) {
            throw stumm();
        }

        @Override
        public List<Kistenshop> alle() {
            throw stumm();
        }

        @Override
        public void schreibe(Kistenshop shop) {
            throw stumm();
        }

        @Override
        public boolean loesche(String welt, int x, int y, int z) {
            throw stumm();
        }
    }
}
