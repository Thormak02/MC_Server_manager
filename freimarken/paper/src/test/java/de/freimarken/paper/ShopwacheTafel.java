package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zusagen der Bukkit-Seite der Kistenshops - am QUELLTEXT und nicht am Verhalten.
 *
 * <p><b>Diese Datei ist ausdruecklich die schwaechere Art Probe, und sie sagt es
 * selbst.</b> {@link Shopwache} und die Bukkit-Haelfte von {@link Shopware} sind ohne
 * laufenden Server nicht zu starten: Sie haengen an {@code ItemStack}, an Blockzustaenden,
 * an sieben Ereignisklassen und an {@code Tag.ALL_SIGNS}, das nach {@code Bukkit.server}
 * greift. Was hier steht, prueft darum TEXT - dass eine Zeile da ist, nicht dass sie
 * wirkt.
 *
 * <p>Dasselbe Maass wie bei {@code GrenzeTest} 31w und 31x, und dieselbe Begruendung:
 * Eine Tafel, die eine Verdrahtung festnagelt, ist mehr als nichts. Eine WIRKLICHE Probe
 * dieser Zweige braeuchte einen Probeserver mit gesetzten Bloecken, einem Spieler und
 * einem Trichter - das ist der Spieltest, und er steht in {@code TODO.md}.
 *
 * <p><b>Was sie festnagelt, sind die vier Entscheidungen, die bei einer Umarbeitung am
 * leichtesten verschwinden</b> - jede von ihnen eine belegte Luecke der Vorbilder:
 * <ul>
 *   <li>Die UNSYMMETRIE des Trichters: nur die Quelle wird geprueft, nie das Ziel.</li>
 *   <li>Dass Explosionen die LISTE kuerzen und nicht das Ereignis abbrechen.</li>
 *   <li>Dass das Schild GEWACHST wird - der Riegel, der ohne dieses Plugin traegt.</li>
 *   <li>Dass der Warenname nirgends VERGLICHEN wird.</li>
 * </ul>
 */
@DisplayName("Die Bukkit-Seite der Kistenshops - Tafeln am Quelltext")
class ShopwacheTafel {

    /**
     * Die neun Ereignisse, auf die {@link Shopwache} hoert - und nur diese.
     *
     * <p>Eine Zahl zu klein ist ein verlorener Schutzweg: Faellt {@code BlockBurnEvent}
     * weg, genuegt ein Feuerzeug neben einer Markthalle. Eine Zahl zu gross ist ein neuer
     * Zweig, den niemand begruendet hat.
     *
     * <p>Was NICHT darin steht, steht im Kopf der Wache mit Begruendung:
     * {@code BlockFromToEvent} (Fluessigkeiten entfernen weder Schild noch Kiste, und das
     * Ereignis feuert bei jedem Flusstakt) und {@code BlockPhysicsEvent} (es ist das
     * allgemeine "ein Block hat sich geaendert" und feuert vielhundertfach je Sekunde).
     */
    private static final Map<String, String> EREIGNISSE = new TreeMap<>(Map.ofEntries(
            Map.entry("beimSchildSchreiben", "SignChangeEvent"),
            Map.entry("beimAbbauen", "BlockBreakEvent"),
            Map.entry("beimWesenSprengen", "EntityExplodeEvent"),
            Map.entry("beimBlockSprengen", "BlockExplodeEvent"),
            Map.entry("beimVerbrennen", "BlockBurnEvent"),
            Map.entry("beimKolbenAus", "BlockPistonExtendEvent"),
            Map.entry("beimKolbenEin", "BlockPistonRetractEvent"),
            Map.entry("beimWesenBlock", "EntityChangeBlockEvent"),
            Map.entry("beimLaubfall", "LeavesDecayEvent"),
            Map.entry("beimGegenstandWandern", "InventoryMoveItemEvent")));

    @Test
    @DisplayName("Jedes Ereignis hat seinen Zuhoerer - und keiner ist dazugekommen")
    void jedesEreignisHatSeinenZuhoerer() {
        String quelle = quelle("Shopwache.java");
        Map<String, String> gefunden = new TreeMap<>();
        for (Map.Entry<String, String> paar : EREIGNISSE.entrySet()) {
            String zeile = "public void " + paar.getKey() + "(" + paar.getValue();
            if (quelle.contains(zeile)) {
                gefunden.put(paar.getKey(), paar.getValue());
            }
        }
        assertEquals(EREIGNISSE, gefunden, "Jeder dieser Zuhoerer ist ein Weg, auf dem"
                + " ein Shopblock verschwinden kann. Faellt einer weg, verschwindet er"
                + " still - und der Shop steht danach in der Datenbank, waehrend sein"
                + " Schild weg ist.");
        // Und die ZAHL der Anmerkungen: Ein zehnter Zuhoerer, der nicht in der Tafel
        // steht, ist ein Zweig, den niemand begruendet hat.
        assertEquals(EREIGNISSE.size(), zaehle(quelle, "@EventHandler"),
                "genau so viele @EventHandler wie Ereignisse in der Tafel");
    }

    @Test
    @DisplayName("Der Trichter prueft die QUELLE und niemals das Ziel")
    void trichterPrueftNurDieQuelle() {
        // DIE UNSYMMETRIE, und sie ist die tragende Entscheidung des ganzen Zuges: Kein
        // Trichter zieht aus einer Shopkiste - nie, auch nicht der des Besitzers.
        // Hineinlegen bleibt erlaubt, denn so wird ein Laden beliefert.
        //
        // Eine Pruefung des ZIELS waere das Gegenteil: Sie verboete das Beliefern und
        // liesse das Abziehen zu. Genau dieser Dreher ist bei einer Umarbeitung in einer
        // Zeile gemacht, und keine Probe ohne Server saehe ihn.
        String zweig = methode("Shopwache.java", "beimGegenstandWandern");

        assertTrue(zweig.contains("ereignis.getSource()"),
                "die Quelle MUSS geprueft werden: " + zweig);
        assertFalse(zweig.contains("getDestination"),
                "das Ziel darf NICHT geprueft werden - sonst ist die Regel verdreht und"
                        + " ein Laden nicht mehr zu beliefern: " + zweig);
    }

    @Test
    @DisplayName("Explosionen kuerzen die LISTE und brechen das Ereignis nicht ab")
    void explosionenKuerzenDieListe() {
        // Ein abgebrochenes Explosionsereignis liesse die GANZE Explosion ausfallen: Kein
        // Block der Umgebung ginge kaputt, und eine Kreatur neben einer Markthalle waere
        // ein Schutzzauber fuer das halbe Viertel.
        String wesen = methode("Shopwache.java", "beimWesenSprengen");
        String block = methode("Shopwache.java", "beimBlockSprengen");
        String kuerzen = methode("Shopwache.java", "schuetzeVorExplosion");

        for (String zweig : List.of(wesen, block)) {
            assertFalse(zweig.contains("setCancelled"),
                    "eine Explosion wird nicht abgebrochen: " + zweig);
            assertTrue(zweig.contains("blockList()"), "" + zweig);
        }
        assertTrue(kuerzen.contains("removeIf"), "gekuerzt wird die Liste: " + kuerzen);
    }

    @Test
    @DisplayName("Das Schild wird GEWACHST - der Riegel, der ohne dieses Plugin traegt")
    void dasSchildWirdGewachst() {
        // Seit Minecraft 1.20 darf jeder ein unbewachstes Schild bearbeiten - ein Kunde
        // koennte an einem fremden Shop den Preis aendern. Wachs ist Vanilla: Es wirkt
        // auch, wenn dieses Plugin fehlt, nach einem Serverwechsel und gegen den
        // Besitzer selbst.
        String zweig = methode("Shopwache.java", "wachse");

        assertTrue(zweig.contains("setWaxed(true)"), "" + zweig);
        assertTrue(zweig.contains("update("), "ohne update bleibt es beim alten Zustand: "
                + zweig);
        // Und der ZWEITE Riegel: Auch wenn das Wachs ausfaellt, wird kein bestehendes
        // Shopschild umgeschrieben.
        String schreiben = methode("Shopwache.java", "beimSchildSchreiben");
        assertTrue(schreiben.contains("anDemSchild(welt,")
                        && schreiben.contains("SHOP_SCHON_EIN_SHOP"),
                "der zweite Riegel fehlt: " + schreiben);
    }

    @Test
    @DisplayName("Der Warenname wird NIRGENDS verglichen")
    void warennameWirdNirgendsVerglichen() {
        // Die Zusage, auf der die Kuerzung in Shopware.kurzname ruht: Dass zwei
        // Materialien denselben Kurznamen bekommen koennen, ist harmlos - aber nur,
        // solange niemand den Namen zum Vergleichen benutzt. Welche Ware ein Shop
        // handelt, entscheidet Warenprobe.inhalt.
        //
        // Gesucht wird der Vergleich und nicht die Nennung: ware().name() darf gesendet,
        // geschrieben und protokolliert werden. Nur verglichen nicht.
        for (Path datei : Quellen.alle()) {
            String text = Quellen.text(datei);
            for (String muster : List.of("ware().name().equals",
                    ".equals(shop.ware().name())", "ware().name() ==",
                    "== shop.ware().name()")) {
                assertFalse(text.contains(muster),
                        datei.getFileName() + " vergleicht den Warennamen (" + muster
                                + ") - er ist gekuerzt und darum nicht eindeutig. Was"
                                + " eine Ware IST, sagt Warenprobe.inhalt.");
            }
        }
    }

    @Test
    @DisplayName("Beide Haelften einer Doppelkiste gehen durch dieselbe Methode")
    void doppelkisteGehtDurchEineMethode() {
        // Zwei Wege zu der Frage "welche Bloecke halten dieselbe Ware" liefen
        // auseinander, und dann waere eine Haelfte bei einem Ereignis geschuetzt und beim
        // anderen nicht. Darum geht kistenbloecke DURCH bloeckeVon und nicht an ihm
        // vorbei.
        String quelle = quelle("Shopware.java");
        String kistenbloecke = methode("Shopware.java", "kistenbloecke");

        assertTrue(kistenbloecke.contains("bloeckeVon("),
                "kistenbloecke muss durch bloeckeVon gehen: " + kistenbloecke);
        assertTrue(quelle.contains("DoubleChest doppel"),
                "die Doppelkiste muss als solche erkannt werden");
        assertTrue(quelle.contains("getLeftSide()") && quelle.contains("getRightSide()"),
                "BEIDE Seiten, nicht eine");
        // getHolder(false): Der Schnappschuss ist bei jedem Trichtertakt eine Kopie des
        // Kisteninhalts. Gefragt wird nur, WO die Kiste steht.
        assertTrue(quelle.contains("getHolder(false)"),
                "ohne das false baut jeder Trichtertakt einen Schnappschuss");
        assertFalse(quelle.contains("inventar.getLocation()"),
                "getLocation gibt bei einer Doppelkiste die MITTE zwischen den Haelften,"
                        + " also eine Stelle ohne Block - dort haengt nie ein Shop");
    }

    @Test
    @DisplayName("Die Erlaubnisliste der Shopkisten nennt drei und keine Shulkerkiste")
    void erlaubnislisteNenntDrei() {
        // Eine ERLAUBNISLISTE und keine Verbotsliste: Eine Verbotsliste ist bei jedem
        // Spielupdate falsch, denn Minecraft bekommt neue Behaelter - und ein neuer
        // stuende als Shop offen, ohne dass jemand darueber nachgedacht haette.
        String quelle = quelle("Shopware.java");

        assertTrue(quelle.contains("Set.of(Material.CHEST, Material.TRAPPED_CHEST,"),
                "" + quelle.substring(quelle.indexOf("ERLAUBTE_KISTEN")));
        assertFalse(quelle.contains("Material.SHULKER_BOX"),
                "QuickShop #1889: Mit einer Shulkerkiste als Shopblock liess sich"
                        + " Shulker in Shulker legen, gegen das Vanilla-Verhalten.");
        assertFalse(quelle.contains("Material.FURNACE")
                        || quelle.contains("Material.DISPENSER"),
                "Ofen und Spender haben Faecher mit verschiedener Bedeutung, und Redstone"
                        + " bedient sie - ein Weg zur Ware, der nicht durch den Shopcode"
                        + " laeuft.");
    }

    @Test
    @DisplayName("Erst die Datenbank, dann der Block - in BEIDEN Abbauwegen")
    void erstDieDatenbankDannDerBlock() {
        // Scheitert das Loeschen, bleibt der Block stehen und der Spieler liest die
        // Stoerung. Umgekehrt stuende ein Shop in der Datenbank, dessen Schild es nicht
        // mehr gibt - und der naechste Spieler, der dort ein Schild aufstellt, bekaeme
        // ein fremdes Geschaeft.
        String aufloesen = methode("Shopwache.java", "loeseAuf");

        int baueAb = aufloesen.indexOf("buch.baueAb(");
        int brechen = aufloesen.indexOf("breakNaturally()");
        assertTrue(baueAb >= 0 && brechen > baueAb,
                "baueAb muss VOR breakNaturally stehen: " + aufloesen);
        assertTrue(aufloesen.contains("GESTOERT")
                        && aufloesen.indexOf("GESTOERT") < brechen,
                "und eine Stoerung muss VOR dem Block abbrechen: " + aufloesen);
    }

    @Test
    @DisplayName("Die Kolben pruefen auch den Block VOR der Kette")
    void kolbenPruefenDenBlockDavor() {
        // Ein Schild, das im Weg steht, wird zerstoert und nicht geschoben - es steht
        // darum NICHT in getBlocks(). Wer nur die Liste prueft, schuetzt die Traeger und
        // verliert das Schild.
        String aus = methode("Shopwache.java", "beimKolbenAus");

        assertTrue(aus.contains("richtungsNachbarn("),
                "der ausfahrende Kolben braucht die Nachbarn in Schubrichtung: " + aus);
        assertTrue(aus.contains("getDirection()"), "" + aus);
    }

    @Test
    @DisplayName("Die Shopwache ist angemeldet, und erst NACH dem Fuellen")
    void wacheIstAngemeldetUndErstNachDemFuellen() {
        // Der Zwischenspeicher wirft, solange er ungefuellt ist - absichtlich, denn "hier
        // ist kein Shop" und "ich weiss es noch nicht" sind verschiedene Antworten. Eine
        // Wache, die auf einen ungefuellten Speicher angemeldet waere, haette beim ersten
        // Blockklick geworfen und den Schutz fuer ALLE Shops aufgehoben.
        String start = methode("FreimarkenPlugin.java", "richteShopsEin");

        int fuellen = start.indexOf("shopablage.fuellen()");
        int anmelden = start.indexOf("registerEvents(shopwache");
        assertTrue(fuellen >= 0, "das Fuellen fehlt: " + start);
        assertTrue(anmelden > fuellen,
                "angemeldet wird NACH dem Fuellen: " + start);
        // Und bei einem Fehlschlag GAR KEINE Wache - mit einer Meldung, die sagt, was
        // dann offen ist.
        assertTrue(start.contains("return;") && start.indexOf("return;") < anmelden,
                "ohne gefuellten Speicher wird nichts angemeldet: " + start);
        assertTrue(start.contains("SEVERE"), "und es steht laut im Protokoll: " + start);
    }

    @Test
    @DisplayName("Das Bundwort kommt aus der config.yml und nicht aus dem Programm")
    void bundwortKommtAusDerDatei() {
        // Jedes Wort, das ein Spieler tippt, steht in einer Datei - derselbe
        // Nutzergrundsatz, nach dem Preise, Rangvorteile und Berufsloehne dort stehen.
        String start = methode("FreimarkenPlugin.java", "richteShopsEin");

        assertTrue(start.contains("handel().bundWort()"),
                "das Bundwort muss aus den Wirtschaftswerten kommen: " + start);
    }

    // --- Werkzeug -------------------------------------------------------------

    private static String quelle(String name) {
        for (Path datei : Quellen.alle()) {
            if (datei.getFileName().toString().equals(name)) {
                return Quellen.text(datei);
            }
        }
        throw new AssertionError(name + " nicht gefunden");
    }

    /**
     * Der Rumpf einer Methode - vom Namen bis zur schliessenden Klammer auf ihrer Ebene.
     *
     * <p>Geklammert und nicht mit einem Muster gesucht: Ein Muster ueber mehrere Zeilen
     * nimmt die naechste Methode mit, und dann ist jede Zusage "steht darin" wertlos -
     * sie traefe auch auf den Nachbarn zu. Dieselbe Falle wie bei einem
     * selbstschliessenden XML-Element.
     */
    private static String methode(String datei, String name) {
        String quelle = quelle(datei);
        int i = deklaration(quelle, name);
        if (i < 0) {
            throw new AssertionError(name + " steht nicht als Deklaration in " + datei);
        }
        int auf = quelle.indexOf('{', i);
        int tiefe = 0;
        for (int j = auf; j < quelle.length(); j++) {
            char zeichen = quelle.charAt(j);
            if (zeichen == '{') {
                tiefe++;
            } else if (zeichen == '}') {
                tiefe--;
                if (tiefe == 0) {
                    return quelle.substring(i, j + 1);
                }
            }
        }
        throw new AssertionError(name + " in " + datei + " hat keine schliessende"
                + " Klammer - die Datei ist kaputt");
    }

    /**
     * Die Stelle, an der diese Methode DEKLARIERT wird - nicht die, an der sie gerufen
     * wird.
     *
     * <p>Gemessen und nicht gedacht: Die erste Fassung suchte nach einem Leerzeichen und
     * dem Namen, und fand damit den AUFRUF von {@code schuetzeVorExplosion} in
     * {@code beimWesenSprengen}. Der Rumpf, den sie dann zurueckgab, war der des
     * Aufrufers - und jede Zusage "steht darin" bezog sich auf die falsche Methode. Die
     * Probe war rot und hatte recht, nur nicht aus dem Grund, den sie nannte.
     *
     * <p>Der Unterschied ist der RUECKGABETYP: Vor einer Deklaration steht mindestens ein
     * Wort ({@code void}, {@code private static String}), vor einem Aufruf nur Einzug.
     */
    private static int deklaration(String quelle, String name) {
        int i = quelle.indexOf(name + "(");
        while (i >= 0) {
            int zeilenanfang = quelle.lastIndexOf('\n', i) + 1;
            String davor = quelle.substring(zeilenanfang, i);
            if (!davor.isBlank() && !davor.contains("(") && !davor.contains(".")
                    && !davor.contains("=")) {
                return i;
            }
            i = quelle.indexOf(name + "(", i + 1);
        }
        return -1;
    }

    private static int zaehle(String text, String stueck) {
        int anzahl = 0;
        int i = text.indexOf(stueck);
        while (i >= 0) {
            anzahl++;
            i = text.indexOf(stueck, i + stueck.length());
        }
        return anzahl;
    }
}
