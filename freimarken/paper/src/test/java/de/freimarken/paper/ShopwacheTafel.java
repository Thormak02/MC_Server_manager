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

    // ================================================== der HANDEL [08.11.2026]

    @Test
    @DisplayName("Gezaehlt wird auf getStorageContents - nie auf getContents")
    void gezaehltWirdAufDenLagerplaetzen() {
        // DIE ZWEITE-HAND-FALLE, und sie ist Geld aus nichts. Beim Spieler sind
        // getContents 41 Plaetze (36 Lager, 4 Ruestung, 1 zweite Hand), getStorageContents
        // 36 - und removeItem bedient nur die Lagerplaetze. Mit getContents gezaehlt ist
        // Ware in der zweiten Hand VORHANDEN und nicht ENTNEHMBAR: Der Spieler bekommt
        // Geld und behaelt die Ware, beliebig wiederholbar.
        //
        // Umgekehrt beim Kauf: Ein freier Helmplatz, als Platz gezaehlt, nimmt keinen
        // Stapel Holz auf - und der Rest ginge verloren. Dieselbe Lehre steht schon in
        // Ladenware.freiePlaetze.
        //
        // An einer KISTE sind beide dasselbe. Der Unterschied zaehlt nur beim Spieler,
        // und genau darum wird er verwechselt.
        String bestand = methode("Shopware.java", "bestand");

        assertTrue(bestand.contains("getStorageContents()"), bestand);
        assertFalse(bestand.contains("getContents()"),
                "getContents enthaelt Ruestung und die zweite Hand - fuenf Faecher, aus"
                        + " denen removeItem nichts nimmt: " + bestand);
    }

    @Test
    @DisplayName("isSimilar steht an GENAU EINER Stelle")
    void isSimilarAnEinerStelle() {
        // Die Frage "ist dieser Stapel DIESE Ware" wird beim Zaehlen, beim Entnehmen und
        // beim Anlegen gestellt. Drei Abschriften laufen auseinander, und dann nimmt der
        // Ausgabegang anderes an, als der Zaehlgang hergibt - so wird aus einem
        // Zaehlfehler eine Buchung ohne Lieferung.
        //
        // Gezaehlt wird NICHT, sondern festgenagelt, WO es steht: Eine Zahl liesse sich
        // hochdrehen, der Rumpf von passt() nicht.
        String quelle = quelle("Shopware.java");
        String passt = methode("Shopware.java", "passt");

        assertTrue(passt.contains("isSimilar"), passt);
        assertEquals(1, quelle.split("isSimilar", -1).length - 1,
                "isSimilar darf NUR in passt() stehen - sonst gibt es zwei Antworten"
                        + " auf die Frage, was diese Ware ist");
    }

    @Test
    @DisplayName("Der BASE64-Text der Warenprobe wird nirgends verglichen")
    void derWarentextWirdNichtVerglichen() {
        // Die andere Richtung derselben Falle: Die Bytes der Warenprobe gehen durch den
        // DATENFIXER, wenn ein Update die Gegenstandsform aendert. Der gespeicherte Text
        // traegt dann die alte Fassung, ein heute aus der Kiste gelesener Gegenstand die
        // neue - die Texte sind verschieden, obwohl die Ware dieselbe ist.
        //
        // Dann meldete JEDER Shop der Welt nach dem Update "ausverkauft", gleichzeitig
        // und ohne Fehlermeldung. Der Text wird nur AUFGELOEST (ausText), nie verglichen.
        String quelle = quelle("Shopware.java");

        assertFalse(quelle.contains("inhalt().equals"), quelle);
        assertFalse(quelle.contains(".equals(shop.ware().inhalt())"), quelle);
        // Aufgeloest wird er an genau einer Stelle, und die liegt in einer eigenen
        // Methode: Sonst entschluesselt der Zaehlgang die Probe je FACH - bei einer
        // Doppelkiste vierundfuenfzigmal je Klick.
        assertEquals(1, quelle.split("ausText\\(", -1).length - 1,
                "die Warenprobe wird EINMAL je Gang aufgeloest, nicht je Fach: "
                        + quelle);
    }

    @Test
    @DisplayName("Bewegt wird mit einer KOPIE, und gelesen wird nur die Restliste")
    void bewegtWirdMitEinerKopie() {
        // Zwei Fallen in einer Zeile. Erstens: addItem und removeItem setzen die Menge
        // des UEBERGEBENEN Stapels herunter, waehrend sie arbeiten - wer hinterher die
        // Warenprobe weiterverwendet, hat eine Probe mit Menge 0. Darum asQuantity, das
        // eine Kopie liefert.
        //
        // Zweitens: "alles gepasst" und "nichts gepasst" lassen die Menge BEIDE
        // unveraendert. Nur die Restliste sagt, was wirklich fehlte - und ihr Schluessel
        // ist der INDEX im Aufruf, nicht ein Inventarplatz. Darum wird sie ueber values()
        // gelesen und nie ueber ihre Schluessel.
        String bewege = methode("Shopware.java", "bewege");
        String portionen = methode("Shopware.java", "portionen");

        assertTrue(portionen.contains("asQuantity("), portionen);
        assertTrue(bewege.contains(".values()"), bewege);
        assertFalse(bewege.contains("keySet()"),
                "der Schluessel der Restliste ist der INDEX im Aufruf und kein"
                        + " Inventarplatz: " + bewege);
    }

    @Test
    @DisplayName("Das Inventar kommt ueber getState(false) - nie als Schnappschuss")
    void inventarOhneSchnappschuss() {
        // DER KUERZESTE WEG ZUR VERDOPPLUNG. getSnapshotInventory() liefert eine KOPIE
        // des Kisteninhalts; ein update() danach schreibt den vorher gelesenen Stand
        // zurueck. Nimmt der Besitzer mit offener Kiste einen Stapel heraus, waehrend ein
        // Kunde kauft, liegt der Stapel danach in der Kiste UND im Rucksack.
        //
        // getState(false) baut gar keinen Schnappschuss, und getInventory() darauf ist
        // das LEBENDE Inventar. Auf dieser Zusage steht schon Ladenware.leere, die eine
        // Truhe wirklich raeumt.
        String quelle = quelle("Shopware.java");
        String inventarAn = methode("Shopware.java", "inventarAn");

        assertTrue(inventarAn.contains("getState(false)"), inventarAn);
        assertFalse(quelle.contains("getSnapshotInventory"), quelle);
        assertFalse(quelle.contains(".update("),
                "ein update() an einer Kiste schreibt einen veralteten Inhalt zurueck -"
                        + " das Wachsen des SCHILDES ist der einzige erlaubte Fall, und"
                        + " der steht in Shopwache: " + quelle);
        // Und der Weg zum Inventar steht an EINER Stelle: Auch das Anlegen geht
        // hindurch, sonst bliebe dort ein getState() ohne Argument stehen.
        assertTrue(methode("Shopware.java", "ausKiste").contains("inventarAn("),
                "auch ausKiste muss durch inventarAn gehen - sonst baut das Anlegen"
                        + " einen Schnappschuss und ist ein falsches Vorbild");
    }

    @Test
    @DisplayName("Die Stapelgroesse wird GEMESSEN, nicht geraten")
    void stapelgroesseWirdGemessen() {
        // Enderperle und Schneeball stapeln zu 16, Werkzeug, Ruestung und jedes
        // verzauberte Stueck zu 1 - und verzauberte Ausruestung ist seit dem 08.11.2026
        // der Normalfall. Seit 1.20.5 kann ausserdem jeder Gegenstand seine eigene
        // Groesse tragen, also ist auch das Material nicht die Quelle.
        //
        // Eine feste 64 machte aus einem Sneak-Klick an einem Spitzhacken-Shop
        // vierundsechzig Stufen.
        String quelle = quelle("Shopware.java");

        assertTrue(quelle.contains("getMaxStackSize()"), quelle);
        assertFalse(quelle.contains("64"),
                "keine feste 64 in dieser Datei - die Stapelgroesse kommt vom"
                        + " Gegenstand: " + quelle);
        // UND die Grenze des BEHAELTERS. Dass sie heute nichts aendert, ist
        // gemessen: Die Nachspritzung N4 vom 08.11.2026 nahm sie weg, und keine
        // Probe wurde rot - in Vanilla stapeln alle Behaelter gleich. Eine
        // Verhaltensprobe dafuer waere also eine, die nichts messen KANN.
        //
        // Sie bleibt trotzdem, und darum bleibt auch diese Tafel: Die Richtung
        // ist die sichere. Ein Behaelter, der kleiner stapelt als der
        // Gegenstand, macht aus der genauen Platzzahl eine UEBERschaetzung -
        // und dann liegt Ware auf dem Boden, die bezahlt ist.
        assertTrue(methode("Shopware.java", "aufEinenPlatz")
                        .contains("inventar.getMaxStackSize()"),
                "die Platzzahl nimmt die KLEINERE der zwei Grenzen");
    }

    // ================================================== der Klickzweig

    @Test
    @DisplayName("Der Klickzweig fragt die HAND - ein Klick kommt bis zu zweimal")
    void derKlickzweigFragtDieHand() {
        // Ohne diese Zeile buchte ein Klick zweimal und bewegte zweimal die Ware. Der
        // sicher erreichbare Weg dorthin ist der SNEAK-Klick, also genau die Geste, mit
        // der ein ganzer Stapel gehandelt wird - dann waere die Doppelbuchung am
        // teuersten.
        String zweig = methode("Shopklick.java", "beimKlicken");

        assertTrue(zweig.contains("nurHaupthand(ereignis.getHand())"), zweig);
    }

    @Test
    @DisplayName("Nach einem Shopklick sind BEIDE Ergebnisse auf DENY")
    void nachDemHandelBeideErgebnisseDeny() {
        // Zwei Dinge auf einmal. Der Linksklick ist in Minecraft der ANFANG eines
        // Abbaus - ohne das Deny schlaegt Lumias Verkaeufer bei jedem Verkauf auf das
        // Schild, und mit dem Teamrecht bricht es wirklich. Und der Rechtsklick mit einem
        // Block in der Hand SETZT einen Block; darum darf die Tuer in der Schutzwache
        // nicht mehr nach der Hand fragen, denn wer Stein verkaufen will, haelt Stein.
        //
        // Der Schutz liegt damit in der WIRKUNG und nicht in der Verweigerung: Der
        // Handel findet statt, die Hand bleibt untaetig.
        String zweig = methode("Shopklick.java", "beimKlicken");

        assertTrue(zweig.contains("setUseInteractedBlock(Event.Result.DENY)"), zweig);
        assertTrue(zweig.contains("setUseItemInHand(Event.Result.DENY)"), zweig);
        // Und sie stehen NACH dem Handel: Ein Deny davor waere auch am eigenen Schild
        // gesetzt, und dann waere das eigene Shopschild unzerbrechlich.
        int handelt = zweig.indexOf("handel.handle(");
        int deny = zweig.indexOf("setUseInteractedBlock");
        assertTrue(handelt > 0 && deny > handelt,
                "das Deny gehoert HINTER den Handel - sonst ist das eigene Schild nicht"
                        + " mehr abzubauen: handle=" + handelt + ", deny=" + deny);
        // Gesetzt wird es nur, wenn der Ausgang es sagt - die Entscheidung steht in
        // Shophandel.Ausgang.verbraucht und ist dort ohne Server geprueft.
        assertTrue(zweig.contains("ausgang.verbraucht()"), zweig);
    }

    @Test
    @DisplayName("Der Klickzweig laeuft NACH der Schutzwache und fragt ihr Ergebnis")
    void klickzweigLaeuftNachDerWache() {
        // Die Reihenfolge haengt an einer PRIORITAET und nicht an der Zeilenlage zweier
        // registerEvents - eine Reihenfolge, die am Startablauf haengt, faellt beim
        // ersten Umstellen um. So macht es das ganze Projekt (Setzwache, Messwache,
        // Verdienstwache).
        //
        // Der Gurt dazu: Auch wenn ein anderes Plugin den Block abgesagt hat, wird hier
        // nicht gehandelt. Das haengt an keiner Annahme darueber, wie isCancelled auf die
        // zwei Ergebnisse abbildet - es fragt sie selbst.
        String quelle = quelle("Shopklick.java");
        String zweig = methode("Shopklick.java", "beimKlicken");

        assertTrue(quelle.contains("priority = EventPriority.HIGH"), quelle);
        assertTrue(quelle.contains("ignoreCancelled = true"), quelle);
        assertTrue(zweig.contains("useInteractedBlock() == Event.Result.DENY"), zweig);
        // EIN Ereignis und kein zweites: Jeder weitere Zweig hier waere eine zweite
        // Stelle, die ueber einen Shopklick entscheidet.
        assertEquals(1, quelle.split("@EventHandler", -1).length - 1,
                "der Handel hoert auf GENAU ein Ereignis: " + quelle);
    }

    @Test
    @DisplayName("Die Handelstuer fragt nach dem SCHILD und nie nach der Kiste")
    void dieTuerFragtNurDasSchild() {
        // Der teuerste Fehler dieses Bereichs. Die naheliegenden Umsetzungen -
        // Shopbuch.istShopkiste und .anDerKiste - antworten beide fuer die KISTE ja, und
        // istShopkiste sagt bei einer Stoerung sogar ja. Waere die Tuer damit zufrieden,
        // oeffnete jeder Kunde mit einem Rechtsklick die fremde Shopkiste und raeumte den
        // Bestand aus; in der Markthalle am Ladenschutz vorbei.
        //
        // Das NEIN an der Kiste haelt ShopklickTest ohne Server fest. Hier steht die
        // andere Haelfte: dass die zwei verfuehrerischen Methoden hier gar nicht
        // vorkommen.
        String quelle = quelle("Shopklick.java");

        assertFalse(quelle.contains("istShopkiste"), quelle);
        assertFalse(quelle.contains("anDerKiste"), quelle);
        assertTrue(quelle.contains("Tag.ALL_SIGNS.isTagged"),
                "gefragt wird die Blockart des SCHILDES: " + quelle);
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
     *
     * <p><b>ZWEIMAL nachgeschaerft, und beide Male am selben Tag gemessen</b>
     * (08.11.2026). Die erste Fassung verwarf jedes Praefix mit einem PUNKT, um
     * {@code waren.bestand(} auszuschliessen - damit fiel aber auch
     * {@code public Shophandel.Bestand bestand(} heraus, denn ein qualifizierter
     * Rueckgabetyp hat einen Punkt. Und sie liess {@code return bewege(} durch:
     * {@code return} ist ein Wort ohne Klammer, Punkt und Gleichheitszeichen, also
     * sah es wie ein Rueckgabetyp aus. Der Rumpf, den sie dann zurueckgab, war der
     * des Aufrufers.
     *
     * <p>Die tragenden Unterschiede sind darum zwei: Vor einem Aufruf steht der
     * Punkt UNMITTELBAR am Namen (nicht irgendwo im Praefix), und ein
     * Schluesselwort kann nie ein Typ sein.
     */
    private static int deklaration(String quelle, String name) {
        int i = quelle.indexOf(name + "(");
        while (i >= 0) {
            int zeilenanfang = quelle.lastIndexOf('\n', i) + 1;
            if (istDeklaration(quelle.substring(zeilenanfang, i))) {
                return i;
            }
            i = quelle.indexOf(name + "(", i + 1);
        }
        return -1;
    }

    /**
     * Worte, die vor einem Namen stehen koennen und doch kein Typ sind.
     *
     * <p>{@code return} ist der, der die Probe getaeuscht hat. Die uebrigen stehen
     * daneben, weil sie in derselben Lage denselben Fehler machen wuerden - und
     * weil eine Liste, die nur den einen gemessenen Fall nennt, beim naechsten
     * wieder falsch ist.
     */
    private static final java.util.Set<String> KEIN_TYP = java.util.Set.of(
            "return", "new", "throw", "assert", "else", "yield");

    /** Ob dieses Praefix vor einer DEKLARATION steht - siehe {@link #deklaration}. */
    private static boolean istDeklaration(String davor) {
        if (davor.isBlank() || davor.contains("(") || davor.contains("=")
                || davor.endsWith(".")) {
            return false;
        }
        String letztes = davor.trim();
        return !KEIN_TYP.contains(letztes.substring(letztes.lastIndexOf(' ') + 1));
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
