package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Wirtschaftsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Die Befehle rund um Parzellen: nehmen, ansehen, Mitbauer, aufgeben.
 *
 * <p>Die Befehle entscheiden nichts. Sie lesen ab, wo der Spieler steht, fragen das
 * {@link Besitzbuch} und tragen die Antwort vor. Jede Regel - Preis, Obergrenze, wer was
 * darf - liegt im Kern und ist dort ohne Server geprueft.
 */
final class ParzellenBefehle {

    /** Mit diesem Recht und einer Zahl dahinter steigt die Obergrenze: {@code .4}, {@code .16}. */
    private static final String RECHT_ANZAHL = "freimarken.parzellen.";

    /** Dasselbe fuer die Felderzahl einer Gruppe: {@code freimarken.gruppe.4}. */
    private static final String RECHT_GRUPPE = "freimarken.gruppe.";

    /** Wie viele Buchungen {@code /mark verlauf} zeigt. */
    private static final int VERLAUF_ZEILEN = 10;

    /**
     * Ein ZUSCHLAG auf die Parzellenzahl: {@code freimarken.extra.parzellen.2}.
     *
     * <p>Dafuer sind die kaufbaren Raenge da. Als zweite absolute Zahl waere eine
     * Mitgliedschaft eine Abkuerzung: Wer am ersten Tag zahlt, haette sofort so viele
     * Parzellen wie jemand mit vierzig Spielstunden, und die erspielte Leiter waere
     * nebensaechlich. Als Zuschlag kommt sie auf das drauf, was er sich erspielt hat -
     * sie belohnt also genau die, die ohnehin viel spielen.
     *
     * <p>Eigener Anfang und nicht {@code freimarken.parzellen.extra.}: Sonst faengt das
     * Recht mit dem Text des anderen an, und die Auswertung dort haengt allein daran,
     * dass "extra.2" sich nicht als Zahl lesen laesst. Das ist kein Riegel, das ist Glueck.
     */
    private static final String RECHT_EXTRA_ANZAHL = "freimarken.extra.parzellen.";

    /** Derselbe Zuschlag fuer die Felderzahl: {@code freimarken.extra.gruppe.1}. */
    private static final String RECHT_EXTRA_GRUPPE = "freimarken.extra.gruppe.";

    private final Besitzbuch buch;

    /** Die Welt, in der Bloecke stehen - fuer das Fuellen und Zurueckbauen des Weges. */
    private final World marktstadt;

    /** Die Welt als Schluesseltext - aus demselben Grund wie in {@link Schutzwache}. */
    private final String welt;

    private final Wegbau wegbau;
    private final Logger protokoll;

    /**
     * Woher die Obergrenzen, die Preise und der Werbetext kommen.
     *
     * <p>Alle drei werden erst beim Befehl gebraucht, und niemand sonst haelt sie - also
     * ist das Holen beim Gebrauch der Eingriff an genau einer Stelle. Vorher standen die
     * Zahlen als {@code final}-Felder hier und waren damit bis zum Neustart eingefroren;
     * ein neu gebautes {@code ParzellenBefehle} haette nichts geholfen, weil die Knoten
     * des Befehlsbaums weiter auf das alte Objekt zeigen.
     *
     * <p>Die Rangwerbung steht ebenfalls dort: Was ein Spieler tun muss, um mehr zu
     * duerfen, weiss nur der Server. Ein eingebauter Hinweis auf einen Shop, den es dort
     * nicht gibt, waere schlechter als gar keiner.
     */
    private final Wirtschaftsstand wirtschaft;

    /**
     * Wie lange ein Spieler zwischen zwei Abschaetzungen warten muss.
     *
     * <p>Drei Sekunden sind im normalen Gebrauch unsichtbar: Wer die Warnung liest und
     * dann bestaetigt, braucht sie kein zweites Mal. Wer die Taste gedrueckt haelt,
     * merkt es - und genau darum geht es.
     */
    private static final Duration ZWISCHEN_ZWEI_ABSCHAETZUNGEN = Duration.ofSeconds(3);

    private final Abklingzeit abschaetzungen = new Abklingzeit(ZWISCHEN_ZWEI_ABSCHAETZUNGEN);

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    ParzellenBefehle(Besitzbuch buch, World marktstadt, Wirtschaftsstand wirtschaft,
                     Logger protokoll, Sprache sprache) {
        this.sprache = sprache;
        this.buch = buch;
        this.marktstadt = marktstadt;
        // Aus der Welt gelesen und nicht daneben uebergeben: Zwei Angaben fuer dieselbe
        // Welt koennen auseinanderlaufen, und der Fehler faellt erst auf, wenn ein Befehl
        // in der Ablage nichts findet, obwohl der Spieler richtig steht.
        this.welt = marktstadt.key().asString();
        this.wegbau = new Wegbau(buch.plan(), new Weltgriff.AufDerWelt(marktstadt));
        this.wirtschaft = wirtschaft;
        this.protokoll = protokoll;
    }

    // --- Anmeldung -------------------------------------------------------------

    LiteralCommandNode<CommandSourceStack> parzelle() {
        return Commands.literal("parzelle")
                .requires(quelle -> quelle.getExecutor() instanceof Player)
                .executes(fuerSpieler(this::zeigeInfo))
                .then(Commands.literal("info").executes(fuerSpieler(this::zeigeInfo)))
                .then(Commands.literal("nehmen").executes(fuerSpieler(this::nehmen)))
                .then(Commands.literal("liste").executes(fuerSpieler(this::zeigeListe)))
                .then(Commands.literal("mitbauer")
                        .executes(fuerSpieler(this::zeigeMitbauer))
                        .then(Commands.literal("hinzu")
                                .then(anwesendeNamen()
                                        .executes(z -> mitOrtUndName(z, this::mitbauerHinzu))))
                        // Eigener Vorschlagsgeber: Zum Austragen sind die Namen auf der
                        // Parzelle gefragt, nicht die aller Anwesenden.
                        .then(Commands.literal("weg")
                                .then(mitbauerNamen()
                                        .executes(z -> mitOrtUndName(z, this::mitbauerWeg)))))
                .then(Commands.literal("aufgeben")
                        .executes(fuerSpieler(this::warneVorAufgeben))
                        // Das zweite Wort ist die Sicherung. Aufgeben zahlt nichts zurueck,
                        // und ein Vertipper darf keine bezahlte Parzelle kosten.
                        //
                        // Die Nummer gehoert mit in den Befehl: Ohne sie gab "bestaetigen"
                        // die Parzelle auf, auf der der Spieler JETZT steht - ein Schritt
                        // ueber den Weg zwischen Warnung und Bestaetigung kostete die
                        // falsche, und zurueck gibt es nichts. Die Warnung schreibt die
                        // Nummer vor, und hier muss sie wieder auftauchen.
                        .then(Commands.literal("bestaetigen")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z",
                                                        IntegerArgumentType.integer())
                                                .executes(z -> mitNummer(z,
                                                        this::aufgeben))))))
                .then(zusammenlegenZweig())
                .then(Commands.literal("auftrennen")
                        // Ohne weiteres Wort loest sich das Feld heraus, auf dem der
                        // Spieler steht. Das ist der haeufige Fall: Wer vier Felder
                        // verbunden hat und eines abgeben will, will nicht alle Wege
                        // dazwischen verlieren und dreimal neu zusammenlegen.
                        .executes(fuerSpieler(this::warneVorHerausloesen))
                        // Zweites Wort und Nummer wie beim Aufgeben: Auftrennen raeumt den
                        // Weg, und was dort stand, ist danach weg.
                        .then(Commands.literal("bestaetigen")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z",
                                                        IntegerArgumentType.integer())
                                                .executes(z -> mitNummer(z,
                                                        this::herausloesen)))))
                        .then(Commands.literal("alles")
                                .executes(fuerSpieler(this::warneVorAuftrennen))
                                .then(Commands.literal("bestaetigen")
                                        .then(Commands.argument("x",
                                                        IntegerArgumentType.integer())
                                                .then(Commands.argument("z",
                                                                IntegerArgumentType.integer())
                                                        .executes(z -> mitNummer(z,
                                                                this::auftrennen)))))))
                .build();
    }

    LiteralCommandNode<CommandSourceStack> mark() {
        return Commands.literal("mark")
                .requires(quelle -> quelle.getExecutor() instanceof Player)
                .executes(fuerSpieler(spieler -> markZeilen(sprache,
                        buch.kasse().guthaben(spieler.getUniqueId()))
                        .forEach(spieler::sendMessage)))
                // Der Verlauf haengt am vorhandenen /mark und bekommt keinen eigenen
                // Befehl: Zwei Befehle, die denselben Stand zeigen, laufen eines Tages
                // auseinander. Mit /konto (Plan 11.1: zahlen, tauschen) wird /mark sein
                // Zweitname, und beide holen Stand und Verlauf aus derselben Methode.
                .then(Commands.literal("verlauf")
                        .executes(fuerSpieler(this::zeigeVerlauf)))
                .build();
    }

    // --- Die Naht: reine Methoden, die nur Zeilen bauen ------------------------
    //
    // Je Zweig eine Methode, die aus einfachen Werten eine List<Component> baut - ohne
    // Player, ohne Bukkit, ohne Besitzbuch. Der Zweig holt die Werte und sendet die
    // Liste, nichts weiter. Der Grund steht im Projekt schon einmal, im Javadoc von
    // SpielzeitBefehle.sende: "damit eine Probe eine Audience mitschreiben lassen kann:
    // Geprueft ist damit nicht nur, WAS in den Zeilen steht, sondern auch, DASS sie
    // gesendet werden." Dasselbe gilt hier, nur eine Stufe strenger - eine Probe braucht
    // nicht einmal eine Audience.
    //
    // WAS NICHT MITWANDERT: eine Wache, die entscheidet, OB ueberhaupt gesendet wird,
    // bleibt im Zweig (Bauplan 9a und 5). Und was im Zweig teuer ist - ein Blick in die
    // Kasse, in die Ablage oder in die Welt - bleibt dort, wo es heute steht. Sonst
    // aendert sich nicht der Wortlaut, aber der Augenblick, in dem der Spieler ihn liest.
    //
    // Der Parameter "Sprache sprache" steht in jeder Signatur, auch wo die Methode ihn
    // noch nicht braucht: Beim Umzug der Saetze aendert sich dann nur der Rumpf und
    // keine Aufrufstelle.

    /**
     * Der Stand und der Hinweis auf den Verlauf - die zwei Zeilen von {@code /mark}.
     *
     * <p>Der Betrag kommt ueber {@link Sprache#betrag} aus {@code waehrung.betrag} - wie
     * jeder Betrag dieser Datei. Hier stand das Wort hart aus der Hilfsklasse
     * {@code Betraege}, die es seit Bauschritt 8 nicht mehr gibt, und damit
     * trugen die zwei Zeilen desselben Befehls zwei verschiedene Geldwege: Zeile 1 das Wort
     * aus dem Programm, die Verlaufszeile darunter das aus der Datei. Zeichengleich war es
     * nur, solange niemand das Wort aendert - und wer es aendert, liest "25.000 Mark" ueber
     * "(Stand 25.000 FM)".
     *
     * @param guthaben was der Spieler hat, schon aus der Kasse geholt
     */
    static List<Component> markZeilen(Sprache sprache, long guthaben) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(MarkSatz.STAND, 1L,
                Einsatz.von("betrag", sprache.betrag(guthaben))));
        zeilen.addAll(sprache.bau(MarkSatz.VERLAUF_HINWEIS, 1L, Einsatz.keine()));
        return List.copyOf(zeilen);
    }

    /**
     * Der Verlauf: entweder die eine Zeile "noch nichts" oder der Kopf und je Buchung
     * eine Zeile.
     *
     * <p>Die Wache auf die leere Liste steht hier und nicht im Zweig, weil in BEIDEN
     * Faellen gesendet wird - sie entscheidet, WAS der Spieler liest, und nicht, OB.
     */
    static List<Component> verlaufZeilen(Sprache sprache, List<Buchung> buchungen,
                                         ZoneId zone) {
        if (buchungen.isEmpty()) {
            return sprache.bau(MarkSatz.VERLAUF_LEER, 1L, Einsatz.keine());
        }
        List<Component> zeilen = new ArrayList<>(sprache.bau(MarkSatz.VERLAUF_KOPF, 1L,
                Einsatz.keine()));
        // Einzug und Farbe stehen seit dem 04.11.2026 IM Satz mark.verlauf.zeile und
        // nicht mehr hier: Verlaufszeile gibt ein fertiges Component zurueck. Hier stand
        // Component.text("  " + text, GRAY) - zwei Leerzeichen und eine Farbe, die der
        // Betreiber nicht erreichen konnte.
        buchungen.forEach(buchung -> zeilen.add(Verlaufszeile.zeile(sprache, buchung, zone)));
        return zeilen;
    }

    /**
     * Die letzten Buchungen - aus derselben Kasse, die auch bucht.
     *
     * <p>Zehn Zeilen, nicht mehr: Der Chat hat keine Seitenzahlen, und wer weiter zurueck
     * will, bekommt mit Plan 12b die Chronik auf der Spielerseite. Eine Obergrenze ist
     * ausserdem Pflicht - ein unbegrenzter Verlauf waere auf einem Server, der Monate
     * laeuft, eine Abfrage, die niemand gewollt hat.
     */
    void zeigeVerlauf(Player spieler) {
        // Die EINE Zone des Servers - dieselbe, in der die Tagesgrenze liegt. Hier stand
        // bis zum 03.11.2026 ZoneId.systemDefault(), mit der Zusage, dass die Zone aus der
        // Einstellungsdatei kommt, sobald es sie gibt. Jetzt gibt es sie. Stehengeblieben
        // waere sie eine zweite Quelle: Auf einem Rechner, dessen Systemzone nicht die
        // eingestellte ist, zeigte diese Zeile eine andere Ortszeit als die Tagesgrenze,
        // mit der dieselbe Buchung gezaehlt wurde.
        verlaufZeilen(sprache, buch.kasse().verlauf(spieler.getUniqueId(), VERLAUF_ZEILEN),
                wirtschaft.werte().zeit().zone()).forEach(spieler::sendMessage);
    }

    // --- Die einzelnen Befehle -------------------------------------------------
    //
    // Die Zweige sind paketweit sichtbar und nicht privat, und zwar genau fuer EINE
    // Probe: AufrufstelleTest schreibt mit einer Audience mit und prueft, WELCHE Methode
    // ein Zweig waehlt, WAS er ihr uebergibt und in welcher Reihenfolge er sendet. Die
    // reinen Methoden darunter deckten das nicht - sie werden unmittelbar aufgerufen, und
    // ein vertauschtes Argument oder ein verwechselter Zweig blieb gruen. Wer einen Zweig
    // wieder privat macht, nimmt dieser Probe ihren Griff.

    void zeigeInfo(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        Rasterplan plan = buch.plan();
        int x = ort.getBlockX();
        int z = ort.getBlockZ();
        Rasterplan.Feld feld = plan.feldBei(x, z);

        if (feld != Rasterplan.Feld.PARZELLE) {
            // Erst nachsehen, ob hier eine Gruppe liegt: Nach einem Zusammenlegen ist
            // diese Stelle Bauland, auch wenn der Rasterplan weiter "Weg" sagt. Ohne
            // diesen Zweig hiesse es hier "gehoert allen", waehrend die Schutzwache
            // nebenan fremde Haende abweist - zwei Antworten auf dieselbe Frage.
            Optional<Parzelle> gruppenboden = buch.zustaendigerBesitz(welt, x, z);
            if (gruppenboden.isPresent()) {
                gruppenbodenZeilen(sprache, namen(gruppenboden.get().eigentuemer()))
                        .forEach(spieler::sendMessage);
                return;
            }
            ohneParzelleZeilen(sprache, feld == Rasterplan.Feld.WEG)
                    .forEach(spieler::sendMessage);
            return;
        }

        int px = plan.parzelleX(x);
        int pz = plan.parzelleZ(z);
        // Der Kopf geht VOR dem Blick in die Ablage hinaus, genau wie bisher: Was der
        // Spieler zuerst liest, haengt nicht davon ab, wie lange eine Abfrage braucht.
        infoKopfZeilen(sprache, px, pz).forEach(spieler::sendMessage);

        Optional<Parzelle> gefunden = buch.parzelleAn(welt, x, z);
        if (gefunden.isEmpty()) {
            if (!buch.grenze().enthaeltParzelle(px, pz)) {
                ausserhalbZeilen(sprache).forEach(spieler::sendMessage);
                return;
            }
            long preis = preise().preisFuerNaechste(anzahl(spieler));
            boolean amLimit = anzahl(spieler) >= hoechstzahl(spieler);
            freiZeilen(sprache, preis, amLimit, hoechstzahl(spieler),
                    wirtschaft.werte().rangWerbung()).forEach(spieler::sendMessage);
            return;
        }

        Parzelle parzelle = gefunden.get();
        boolean meine = parzelle.gehoert(spieler.getUniqueId());
        vergebenZeilen(sprache, namen(parzelle.eigentuemer()), parzelle.preis(),
                parzelle.mitbauer().isEmpty() ? null : namenListe(parzelle.mitbauer()),
                parzelle.hatElternteil(),
                parzelle.hatElternteil() ? buch.gruppenFelder(welt, x, z).size() : 0,
                meine).forEach(spieler::sendMessage);
    }

    /** Dass hier eine Gruppe liegt, wo der Rasterplan noch "Weg" sagt - und wem sie gehoert. */
    static List<Component> gruppenbodenZeilen(Sprache sprache, String eigentuemer) {
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(ParzellenSatz.INFO_GRUPPENBODEN, 1L, Einsatz.keine()));
        // Dieselbe Zeile wie bei einer vergebenen Parzelle, also derselbe Schluessel:
        // Wer sie umschreibt, soll das nicht an zwei Stellen tun muessen.
        zeilen.addAll(sprache.bau(ParzellenSatz.INFO_EIGENTUEMER, 1L,
                Einsatz.von("name", eigentuemer)));
        return List.copyOf(zeilen);
    }

    /** Die eine Zeile fuer eine Stelle ohne Parzelle: Weg oder Mauer. */
    static List<Component> ohneParzelleZeilen(Sprache sprache, boolean aufDemWeg) {
        // Der Ternaer steht jetzt ueber dem SCHLUESSEL und nicht mehr ueber dem Wortlaut:
        // Zwei Saetze bleiben zwei Saetze, nur stehen sie in der Datei.
        return sprache.bau(aufDemWeg ? ParzellenSatz.INFO_WEG : ParzellenSatz.INFO_MAUER,
                1L, Einsatz.keine());
    }

    /** Der Kopf von {@code /parzelle info}: die Nummer. */
    static List<Component> infoKopfZeilen(Sprache sprache, int px, int pz) {
        return sprache.bau(ParzellenSatz.INFO_KOPF, 1L, nummer(px, pz));
    }

    /**
     * Eine Parzellennummer als zwei Werte - NACKT und ohne Tausenderpunkt.
     *
     * <p>{@code Einsatz.mit(name, long)} schickt eine Zahl durch die deutsche Zahlform,
     * und das ist fuer Geld richtig und fuer eine Nummer falsch: Die Parzelle 1000/0 hiess
     * heute "1000/0" und wuerde damit "1.000/0". Gemessen gilt dasselbe fuer jede andere
     * gezaehlte Zahl dieser Zweige - nur Geldbetraege tragen den Punkt.
     */
    private static Einsatz nummer(int px, int pz) {
        return Einsatz.von("x", String.valueOf(px)).mit("z", String.valueOf(pz));
    }

    /** Eine Parzelle, die es gibt, aber nicht zu haben ist. */
    static List<Component> ausserhalbZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.INFO_AUSSERHALB, 1L, Einsatz.keine());
    }

    /**
     * Eine freie Parzelle: der Preis - und am Limit die Absage samt Werbung.
     *
     * <p>Der Preis bleibt stehen, die Einladung nicht. Wer vier von vier Parzellen hat,
     * soll ruhig sehen, was die fuenfte kosten WUERDE - aber "/parzelle nehmen" darunter
     * ist eine Aufforderung, die an derselben Stelle mit einer Absage endet. Genau das ist
     * der Unterschied zwischen Werbung und Irrefuehrung.
     */
    static List<Component> freiZeilen(Sprache sprache, long preis, boolean amLimit,
                                      int hoechstzahl, String rangWerbung) {
        // Zwei Schluessel und nicht einer mit Wache: Am Limit faellt nicht nur die
        // Einladung weg, auch das Gruen des Preises. Beides steht jetzt im Satz.
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                amLimit ? ParzellenSatz.INFO_FREI_AM_LIMIT : ParzellenSatz.INFO_FREI, 1L,
                Einsatz.von("preis", preisWort(sprache, preis))));
        if (amLimit) {
            zeilen.addAll(sprache.bau(ParzellenSatz.INFO_AM_LIMIT, hoechstzahl,
                    Einsatz.von("anzahl", String.valueOf(hoechstzahl))));
            zeilen.addAll(werbungZeilen(sprache, rangWerbung));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Der Preis als Wort: das Wort fuer die Null, oder ein Betrag.
     *
     * <p>Ein {@code Art.WORT} fuer die Null und kein zweiter Satz je Stelle: Drei Zeilen
     * nennen den Preis, und der Betreiber soll "gratis" einmal aendern koennen.
     */
    private static String preisWort(Sprache sprache, long preis) {
        return preis == 0L
                ? sprache.wort(ParzellenSatz.GRATIS, Einsatz.keine())
                : sprache.betrag(preis);
    }

    /**
     * Eine vergebene Parzelle: Eigentuemer, Preis, Mitbauer, Gruppe - und die Befehle,
     * die dem Eigentuemer offenstehen.
     *
     * @param mitbauer       die Namen, oder {@code null}, wenn keiner eingetragen ist
     * @param gruppenfelder  wie viele Felder die Gruppe hat; ohne Gruppe bedeutungslos
     */
    static List<Component> vergebenZeilen(Sprache sprache, String eigentuemer,
                                          long gekauftFuer, String mitbauer,
                                          boolean teilEinerGruppe, int gruppenfelder,
                                          boolean meine) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                ParzellenSatz.INFO_EIGENTUEMER, 1L, Einsatz.von("name", eigentuemer)));
        zeilen.addAll(sprache.bau(ParzellenSatz.INFO_GEKAUFT_FUER, 1L,
                Einsatz.von("preis", gekauftFuer == 0L
                        ? sprache.wort(ParzellenSatz.GRATIS_DIE_ERSTE, Einsatz.keine())
                        : sprache.betrag(gekauftFuer))));
        if (mitbauer != null) {
            zeilen.addAll(sprache.bau(ParzellenSatz.INFO_MITBAUER, 1L,
                    Einsatz.von("namen", mitbauer)));
        }
        if (teilEinerGruppe) {
            // Art.SATZ und nicht Art.GEZAEHLT: Dieser Satz sagt heute auch bei einem Feld
            // "Feldern". Eine Einzahlform waere eine inhaltliche Verbesserung und gehoert
            // nicht nebenbei in einen Umzug, der das Bild gleich halten soll.
            zeilen.addAll(sprache.bau(ParzellenSatz.INFO_GRUPPE, 1L,
                    Einsatz.von("anzahl", String.valueOf(gruppenfelder))));
            if (meine) {
                zeilen.addAll(sprache.bau(ParzellenSatz.INFO_AUFTRENNEN_HINWEIS, 1L,
                        Einsatz.keine()));
            }
        } else if (meine) {
            zeilen.addAll(sprache.bau(ParzellenSatz.INFO_ZUSAMMENLEGEN_HINWEIS, 1L,
                    Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    void nehmen(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        int hoechstzahl = hoechstzahl(spieler);
        var entscheid = buch.nehmen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), hoechstzahl);

        switch (entscheid.bescheid()) {
            case ERTEILT -> {
                Parzelle neu = entscheid.parzelle().orElseThrow();
                // Der Blick in die Kasse nur, wo ein Rest genannt wird: Bei der ersten,
                // gratis vergebenen Parzelle fragte auch bisher niemand danach.
                nehmenErteiltZeilen(sprache, neu.x(), neu.z(), entscheid.kosten(),
                        entscheid.kosten() == 0L
                                ? 0L : buch.kasse().guthaben(spieler.getUniqueId()))
                        .forEach(spieler::sendMessage);
            }
            case KEINE_PARZELLE -> nehmenKeinBaulandZeilen(sprache)
                    .forEach(spieler::sendMessage);
            case SCHON_VERGEBEN -> nehmenSchonVergebenZeilen(sprache,
                    namen(entscheid.parzelle().orElseThrow().eigentuemer()))
                    .forEach(spieler::sendMessage);
            case GRENZE_ERREICHT -> nehmenGrenzeZeilen(sprache, hoechstzahl,
                    wirtschaft.werte().rangWerbung()).forEach(spieler::sendMessage);
            case ZU_WENIG_MARK -> nehmenZuWenigZeilen(sprache, entscheid.kosten(),
                    buch.kasse().guthaben(spieler.getUniqueId()))
                    .forEach(spieler::sendMessage);
            case AUSSERHALB -> nehmenAusserhalbZeilen(sprache, buch.grenze().von(),
                    buch.grenze().bis()).forEach(spieler::sendMessage);
            default -> nehmenSonstZeilen(sprache, entscheid.bescheid())
                    .forEach(spieler::sendMessage);
        }
    }

    /**
     * Die Parzelle gehoert jetzt ihm - die Nummer und die Rechnung.
     *
     * <p><b>Bei {@code kosten == 0} ist {@code guthaben} bedeutungslos, und der Sendeort
     * gibt dort eine 0 herein, die es in der Kasse nie gab.</b> Dass diese erfundene Zahl
     * nicht in den Satz kommt, haengt allein an der Wache hier - der Sendeort hat
     * dieselbe Bedingung, aber sie entscheidet dort nur, ob die Kasse ueberhaupt gefragt
     * wird. Darum ruft {@code SendewegTest} den Gratisfall auch mit einem HOHEN Guthaben
     * auf: Faellt die Wache, liest der Spieler "Bezahlt: 0 Mark - Rest: 0 Mark", obwohl
     * er 99.000 hat, und nur jene Zeile ist dann rot.
     *
     * @param guthaben der Rest nach dem Kauf; bei {@code kosten == 0} bedeutungslos
     */
    static List<Component> nehmenErteiltZeilen(Sprache sprache, int px, int pz,
                                               long kosten, long guthaben) {
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(ParzellenSatz.NEHMEN_ERTEILT, 1L, nummer(px, pz)));
        zeilen.addAll(kosten == 0L
                ? sprache.bau(ParzellenSatz.NEHMEN_ERSTE_GRATIS, 1L, Einsatz.keine())
                : sprache.bau(ParzellenSatz.BEZAHLT, 1L,
                        rechnung(sprache, kosten, guthaben)));
        return List.copyOf(zeilen);
    }

    /**
     * Bezahlt und Rest - die zwei Werte der Rechnung.
     *
     * <p>Eigene Methode, weil {@code parzelle.bezahlt} denselben Satz auch nach dem
     * Zusammenlegen tragen soll. Beide Betraege gehen durch {@link Sprache#betrag}, also
     * durch {@code waehrung.betrag} - so wie jeder Betrag dieser Datei.
     */
    private static Einsatz rechnung(Sprache sprache, long kosten, long guthaben) {
        return Einsatz.von("betrag", sprache.betrag(kosten))
                .mit("rest", sprache.betrag(guthaben));
    }

    /** Er steht gar nicht auf Bauland. */
    static List<Component> nehmenKeinBaulandZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.NEHMEN_KEIN_BAULAND, 1L, Einsatz.keine());
    }

    /** Die Parzelle hat schon einen Eigentuemer. */
    static List<Component> nehmenSchonVergebenZeilen(Sprache sprache, String eigentuemer) {
        return sprache.bau(ParzellenSatz.NEHMEN_SCHON_VERGEBEN, 1L,
                Einsatz.von("name", eigentuemer));
    }

    /** Sein Rang gibt nicht mehr her - mit Zahl und mit Werbung. */
    static List<Component> nehmenGrenzeZeilen(Sprache sprache, int hoechstzahl,
                                              String rangWerbung) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                ParzellenSatz.NEHMEN_GRENZE, hoechstzahl,
                Einsatz.von("anzahl", String.valueOf(hoechstzahl))));
        zeilen.addAll(werbungZeilen(sprache, rangWerbung));
        return List.copyOf(zeilen);
    }

    /** Das Geld reicht nicht - was sie kostet und was er hat. */
    static List<Component> nehmenZuWenigZeilen(Sprache sprache, long kosten, long guthaben) {
        // Eine Zeile und keine drei Stuecke: Wer lieber erst das Guthaben nennt, stellt
        // den Satz in der Datei um, und das Gold wandert mit dem Betrag.
        return sprache.bau(ParzellenSatz.NEHMEN_ZU_WENIG, 1L,
                Einsatz.von("betrag", sprache.betrag(kosten))
                        .mit("guthaben", sprache.betrag(guthaben)));
    }

    /** Hinter der Stadtgrenze - mit den Nummern, die es gibt. */
    static List<Component> nehmenAusserhalbZeilen(Sprache sprache, int von, int bis) {
        return sprache.bau(ParzellenSatz.NEHMEN_AUSSERHALB, 1L,
                Einsatz.von("von", String.valueOf(von))
                        .mit("bis", String.valueOf(bis)));
    }

    /** Der Rest, den dieser Zweig nicht eigens benennt. */
    static List<Component> nehmenSonstZeilen(Sprache sprache,
                                             Besitzbuch.Bescheid bescheid) {
        return sprache.bau(ParzellenSatz.NEHMEN_SONST, 1L,
                Einsatz.von("bescheid", bescheid.name()));
    }

    void zeigeListe(Player spieler) {
        List<Parzelle> meine = buch.parzellenVon(welt, spieler.getUniqueId());
        int hoechstzahl = hoechstzahl(spieler);
        listeZeilen(sprache, meine, hoechstzahl,
                preise().preisFuerNaechste(meine.size())).forEach(spieler::sendMessage);
    }

    /**
     * Die eigenen Parzellen - oder die Einladung, die erste zu nehmen.
     *
     * <p>{@code Parzelle} ist ein Satz aus dem Kern, kein Serverobjekt: Deshalb darf die
     * Liste hier herein, und deshalb kann eine Probe diese Zeilen ohne Welt bauen.
     *
     * @param naechsterPreis was die naechste kosten wuerde; nur unter der Hoechstzahl genannt
     */
    static List<Component> listeZeilen(Sprache sprache, List<Parzelle> meine,
                                       int hoechstzahl, long naechsterPreis) {
        if (meine.isEmpty()) {
            return sprache.bau(ParzellenSatz.LISTE_LEER, 1L, Einsatz.keine());
        }
        List<Component> zeilen = new ArrayList<>(sprache.bau(ParzellenSatz.LISTE_KOPF, 1L,
                Einsatz.von("anzahl", String.valueOf(meine.size()))
                        .mit("hoechstzahl", String.valueOf(hoechstzahl))));
        for (Parzelle parzelle : meine) {
            // Die zwei Anhaenge sind ENTWEDER ihr Wortstueck ODER leer. Ein leerer Wert
            // verschwindet rueckstandslos, weil ein eingesetzter Wert ein Blatt ist -
            // darum braucht die Zeile keine vier Fassungen fuer vier Kombinationen.
            zeilen.addAll(sprache.bau(ParzellenSatz.LISTE_ZEILE, 1L,
                    nummer(parzelle.x(), parzelle.z())
                            .mit("preis", preisWort(sprache, parzelle.preis()))
                            .mit("mitbauer", parzelle.mitbauer().isEmpty() ? ""
                                    : sprache.wort(ParzellenSatz.LISTE_MITBAUER,
                                            Einsatz.von("anzahl", String.valueOf(
                                                    parzelle.mitbauer().size()))))
                            // Sonst saehe eine Gruppe aus zwei Feldern in der Liste
                            // genauso aus wie zwei einzelne Parzellen - und der Spieler
                            // wuesste nicht, warum /parzelle aufgeben bei ihnen nicht geht.
                            .mit("gruppe", parzelle.hatElternteil()
                                    ? sprache.wort(ParzellenSatz.LISTE_GRUPPE,
                                            Einsatz.keine())
                                    : "")));
        }
        if (meine.size() < hoechstzahl) {
            zeilen.addAll(sprache.bau(ParzellenSatz.LISTE_NAECHSTE, 1L,
                    Einsatz.von("betrag", sprache.betrag(naechsterPreis))));
        }
        return List.copyOf(zeilen);
    }

    void zeigeMitbauer(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        Optional<Parzelle> gefunden = buch.zustaendigerBesitz(welt, ort.getBlockX(),
                ort.getBlockZ());
        if (gefunden.isEmpty()) {
            mitbauerKeineParzelleZeilen(sprache).forEach(spieler::sendMessage);
            return;
        }
        Parzelle parzelle = gefunden.get();
        mitbauerZeilen(sprache, parzelle.x(), parzelle.z(),
                parzelle.mitbauer().isEmpty() ? null : namenListe(parzelle.mitbauer()),
                parzelle.gehoert(spieler.getUniqueId())).forEach(spieler::sendMessage);
    }

    /** Hier gibt es keine Parzelle, zu der eine Mitbauerliste gehoeren wuerde. */
    static List<Component> mitbauerKeineParzelleZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.MITBAUER_KEINE_PARZELLE, 1L, Einsatz.keine());
    }

    /**
     * Die Mitbauerliste - und fuer den Eigentuemer die Befehle dazu.
     *
     * @param mitbauer die Namen, oder {@code null}, wenn niemand eingetragen ist
     */
    static List<Component> mitbauerZeilen(Sprache sprache, int px, int pz, String mitbauer,
                                          boolean meine) {
        // Das Wort fuer die leere Liste ist ein WERT und kommt darum aus der Datei:
        // Es wird in mitbauer.kopf eingesetzt, wo der Betreiber es sonst nicht
        // erreichen koennte.
        String namen = mitbauer == null
                ? sprache.wort(ParzellenSatz.MITBAUER_NIEMAND, Einsatz.keine())
                : mitbauer;
        List<Component> zeilen = new ArrayList<>(sprache.bau(ParzellenSatz.MITBAUER_KOPF,
                1L, nummer(px, pz).mit("namen", namen)));
        if (meine) {
            zeilen.addAll(sprache.bau(ParzellenSatz.MITBAUER_BEFEHLE, 1L,
                    Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    void mitbauerHinzu(Player spieler, Location ort, String name) {
        Optional<UUID> betroffener = findeSpieler(name);
        if (betroffener.isEmpty()) {
            unbekannterSpielerZeilen(sprache).forEach(spieler::sendMessage);
            return;
        }
        var entscheid = buch.mitbauerHinzufuegen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), betroffener.get());
        switch (entscheid.bescheid()) {
            case ERTEILT -> mitbauerErteiltZeilen(sprache, name)
                    .forEach(spieler::sendMessage);
            case SCHON_EINGETRAGEN -> mitbauerSchonDrinZeilen(sprache, name)
                    .forEach(spieler::sendMessage);
            default -> absage(spieler, entscheid.bescheid());
        }
    }

    /** Diesen Namen kennt der Server nicht. */
    static List<Component> unbekannterSpielerZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.MITBAUER_UNBEKANNT, 1L, Einsatz.keine());
    }

    /** Eingetragen. */
    static List<Component> mitbauerErteiltZeilen(Sprache sprache, String name) {
        return sprache.bau(ParzellenSatz.MITBAUER_HINZU_ERTEILT, 1L,
                Einsatz.von("name", name));
    }

    /** Stand schon auf der Liste. */
    static List<Component> mitbauerSchonDrinZeilen(Sprache sprache, String name) {
        return sprache.bau(ParzellenSatz.MITBAUER_SCHON_DRIN, 1L,
                Einsatz.von("name", name));
    }

    /**
     * Traegt einen Mitbauer aus - und sucht ihn zuerst auf der Parzelle selbst.
     *
     * <p>Das ist der Unterschied zum Eintragen: Dort muss ein Spieler erst gefunden werden,
     * hier steht er schon auf einer Liste. Vorher ging auch das Austragen ueber die
     * Spielerhistorie, und damit war genau der Eintrag unentfernbar, den die Anzeige als
     * UUID-Anfang zeigt - weil es zu ihm keinen Namen mehr gibt, den jemand nachschlagen
     * koennte. Gesucht wird gegen dieselbe Zeichenfolge, die die Anzeige ausgibt.
     */
    void mitbauerWeg(Player spieler, Location ort, String name) {
        Optional<Parzelle> gefunden = buch.zustaendigerBesitz(welt, ort.getBlockX(),
                ort.getBlockZ());
        Optional<UUID> betroffener = gefunden
                .flatMap(parzelle -> ausDerListe(parzelle, name))
                .or(() -> findeSpieler(name));
        if (betroffener.isEmpty()) {
            niemandMitDemNamenZeilen(sprache).forEach(spieler::sendMessage);
            return;
        }
        var entscheid = buch.mitbauerEntfernen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), betroffener.get());
        switch (entscheid.bescheid()) {
            case ERTEILT -> mitbauerWegErteiltZeilen(sprache, name)
                    .forEach(spieler::sendMessage);
            case NICHT_EINGETRAGEN -> mitbauerNichtDrinZeilen(sprache, name)
                    .forEach(spieler::sendMessage);
            default -> absage(spieler, entscheid.bescheid());
        }
    }

    /** Auf dieser Parzelle steht niemand mit diesem Namen. */
    static List<Component> niemandMitDemNamenZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.MITBAUER_NIEMAND_MIT_DEM_NAMEN, 1L,
                Einsatz.keine());
    }

    /** Ausgetragen. */
    static List<Component> mitbauerWegErteiltZeilen(Sprache sprache, String name) {
        return sprache.bau(ParzellenSatz.MITBAUER_WEG_ERTEILT, 1L,
                Einsatz.von("name", name));
    }

    /** Stand gar nicht auf der Liste. */
    static List<Component> mitbauerNichtDrinZeilen(Sprache sprache, String name) {
        return sprache.bau(ParzellenSatz.MITBAUER_NICHT_DRIN, 1L,
                Einsatz.von("name", name));
    }

    /** Einen Eintrag der Mitbauerliste an der Zeichenfolge erkennen, die angezeigt wird. */
    private static Optional<UUID> ausDerListe(Parzelle parzelle, String eingabe) {
        for (UUID kandidat : parzelle.mitbauer()) {
            if (namen(kandidat).equalsIgnoreCase(eingabe)
                    || kandidat.toString().equalsIgnoreCase(eingabe)) {
                return Optional.of(kandidat);
            }
        }
        return Optional.empty();
    }

    void warneVorAufgeben(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        Optional<Parzelle> gefunden = buch.zustaendigerBesitz(welt, ort.getBlockX(),
                ort.getBlockZ());
        if (gefunden.isEmpty() || !gefunden.get().gehoert(spieler.getUniqueId())) {
            nichtDeineParzelleZeilen(sprache).forEach(spieler::sendMessage);
            return;
        }
        Parzelle parzelle = gefunden.get();
        if (parzelle.hatElternteil()) {
            // Aufgeben verweigert auf einem Gruppenkind. Das sagt sich hier besser als
            // erst nach der Bestaetigung, mit dem allgemeinen Satz aus absage().
            aufgebenGruppeZeilen(sprache).forEach(spieler::sendMessage);
            return;
        }
        aufgebenWarnungZeilen(sprache, parzelle.x(), parzelle.z())
                .forEach(spieler::sendMessage);
    }

    /** Hier steht er nicht auf eigenem Boden. */
    static List<Component> nichtDeineParzelleZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.AUFGEBEN_NICHT_DEINE, 1L, Einsatz.keine());
    }

    /** Ein Gruppenkind laesst sich nicht einzeln aufgeben. */
    static List<Component> aufgebenGruppeZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.AUFGEBEN_GRUPPE, 1L, Einsatz.keine());
    }

    /**
     * Die Warnung vor dem Aufgeben - EIN Satz, der drei Chatzeilen ergibt.
     *
     * <p>Drei Sendestellen wurden eine Zeile der Datei mit zwei Umbruchmarken. Jede der
     * drei traegt ihre eigene Farbe, und genau das ist der Grund: Drei Schluessel koennte
     * der Betreiber faerben, aber nicht umstellen.
     */
    static List<Component> aufgebenWarnungZeilen(Sprache sprache, int px, int pz) {
        return sprache.bau(ParzellenSatz.AUFGEBEN_WARNUNG, 1L, nummer(px, pz));
    }

    /**
     * Gibt die Parzelle auf, die im Befehl steht - nicht die, auf der der Spieler steht.
     *
     * <p>Der Unterschied ist der ganze Punkt: Vorher las dieser Zweig den Ort neu ein, und
     * ein Schritt ueber den Weg zwischen Warnung und Bestaetigung gab die Nachbarparzelle
     * auf. Jetzt muss der Spieler immer noch auf der Parzelle stehen, die er aufgibt, aber
     * es muss auch dieselbe sein, die im Befehl steht.
     */
    void aufgeben(Player spieler, int gemeintX, int gemeintZ) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        if (!stehtAuf(spieler, ort, gemeintX, gemeintZ,
                ParzellenSatz.TAT_NICHTS_AUFGEGEBEN)) {
            return;
        }
        var entscheid = buch.aufgeben(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (entscheid.erteilt()) {
            Parzelle weg = entscheid.parzelle().orElseThrow();
            aufgegebenZeilen(sprache, weg.x(), weg.z()).forEach(spieler::sendMessage);
            return;
        }
        absage(spieler, entscheid.bescheid());
    }

    /** Die Parzelle ist wieder frei. */
    static List<Component> aufgegebenZeilen(Sprache sprache, int px, int pz) {
        return sprache.bau(ParzellenSatz.AUFGEBEN_ERTEILT, 1L, nummer(px, pz));
    }

    // --- Zusammenlegen und Auftrennen ------------------------------------------

    /**
     * Der Zweig {@code /parzelle zusammenlegen <richtung>} - vier Literale, kein Textfeld.
     *
     * <p>Brigadier vergleicht die Literale selbst und schlaegt sie vor. Ein Textfeld
     * muesste den Namen zurueck in eine {@link Richtung} uebersetzen, und dabei liegt die
     * Falle mit dem Gebietsschema: {@code toUpperCase()} macht aus "sued" auf einem
     * tuerkischen Rechner "SUED" mit Punkt auf dem I, und der Befehl scheitert auf genau
     * einem Rechner der Welt. Diese Falle wird hier gar nicht gebaut.
     */
    private LiteralArgumentBuilder<CommandSourceStack> zusammenlegenZweig() {
        LiteralArgumentBuilder<CommandSourceStack> zweig = Commands.literal("zusammenlegen");
        for (Richtung richtung : Richtung.values()) {
            zweig.then(Commands.literal(richtung.wort())
                    .executes(fuerSpieler(spieler -> warneVorZusammenlegen(spieler, richtung)))
                    // Die Nummer gehoert in die Bestaetigung, genau wie beim Aufgeben: Ohne
                    // sie legt ein Schritt ueber den Weg zwischen Warnung und Bestaetigung
                    // ein anderes Paar zusammen - zu einem anderen Preis, und bezahlt ist
                    // es dann.
                    .then(Commands.literal("bestaetigen")
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                    .then(Commands.argument("z",
                                                    IntegerArgumentType.integer())
                                            .executes(z -> mitNummer(z,
                                                    (spieler, gemeintX, gemeintZ)
                                                            -> zusammenlegen(spieler,
                                                            richtung, gemeintX,
                                                            gemeintZ)))))));
        }
        return zweig;
    }

    /**
     * Was ein Zusammenlegen kosten wuerde - und was es mit dem Weg macht.
     *
     * <p>Gerechnet wird mit {@link Besitzbuch#pruefeZusammenlegen}, also mit derselben
     * Formel, die anschliessend abbucht. Eine eigene Rechnung hier waere der zweite Weg zur
     * selben Tat, und einer von beiden nennt irgendwann einen anderen Betrag.
     */
    void warneVorZusammenlegen(Player spieler, Richtung richtung) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        int felder = gruppenFelder(spieler);
        var entscheid = buch.pruefeZusammenlegen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), richtung, felder);
        if (entscheid.bescheid() == Besitzbuch.Bescheid.GRUPPE_ZU_GROSS) {
            gruppeZuGrossZeilen(sprache, felder, wirtschaft.werte().rangWerbung())
                    .forEach(spieler::sendMessage);
            return;
        }
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }

        Rasterplan plan = buch.plan();
        int px = plan.parzelleX(ort.getBlockX());
        int pz = plan.parzelleZ(ort.getBlockZ());
        long guthaben = buch.kasse().guthaben(spieler.getUniqueId());

        zusammenlegenWarnungZeilen(sprache, px, pz, richtung, entscheid.kosten(), guthaben)
                .forEach(spieler::sendMessage);
    }

    /**
     * Die Gruppe waere zu gross - mit Zahl und mit Werbung.
     *
     * <p>Mit Zahl und nicht mit dem allgemeinen Satz: "dein Rang erlaubt nicht mehr"
     * laesst offen, was er erlaubt.
     *
     * <p><b>Es ist derselbe Bescheid und darum derselbe Weg.</b> Gemessen sendet dieser
     * Zweig woertlich das, was {@code absageZeilen} fuer {@code GRUPPE_ZU_GROSS} sendet -
     * bis zum zweiten Zug baute er die Worte nur selbst, und darum trug der Schluessel in
     * der Vorlage die Marke {@code HALB IN KRAFT:}. Jetzt geht er durch dieselbe Methode:
     * WELCHER Satz zu dem Bescheid gehoert und WELCHE Zahl er nennt, entscheidet allein
     * {@code Sprachschluessel}. Zwei Wege zur selben Tat laufen irgendwann auseinander,
     * und ein zweiter namentlicher Griff auf den Schluessel haette ausserdem
     * {@code GrenzeTest.jederSchluesselWirdGesendet} rot gemacht: Der Bescheid wird ueber
     * eine Familienfunktion gewaehlt und nirgends getippt.
     */
    static List<Component> gruppeZuGrossZeilen(Sprache sprache, int felder,
                                               String rangWerbung) {
        // amLimit false und hoechstzahl 0: Fuer diesen Bescheid sagt
        // Sprachschluessel.zahlquelle GRUPPENFELDER, also nennt der Satz felder. Die 0
        // steht da, damit ein Griff zur falschen Quelle im Spiel als "0 Parzellen"
        // auffaellt und SendewegTest 33m rot wird - und nicht stumm plausibel bleibt.
        return absageZeilen(sprache, Besitzbuch.Bescheid.GRUPPE_ZU_GROSS, false, 0,
                felder, rangWerbung);
    }

    /**
     * Die Warnung vor dem Zusammenlegen: Gebuehr und was mit dem Weg geschieht - und
     * zuletzt entweder die Bestaetigungszeile oder der Hinweis auf das fehlende Geld.
     */
    static List<Component> zusammenlegenWarnungZeilen(Sprache sprache, int px, int pz,
                                                      Richtung richtung, long kosten,
                                                      long guthaben) {
        // Die fuenf Zeilen sind EIN Satz der Datei - von Hand umgebrochen, also mit
        // Umbruchmarken (Bauplan 7). Der Nachbar wird hier gerechnet und als WERT
        // uebergeben: Im Satz steht seine Nummer, nicht die Richtung.
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                ParzellenSatz.ZUSAMMENLEGEN_WARNUNG, 1L, nummer(px, pz)
                        .mit("nachbar-x", String.valueOf(px + richtung.dx()))
                        .mit("nachbar-z", String.valueOf(pz + richtung.dz()))
                        .mit("betrag", sprache.betrag(kosten))
                        .mit("guthaben", sprache.betrag(guthaben))));
        if (guthaben < kosten) {
            // Das Geld prueft pruefeZusammenlegen absichtlich nicht - es ist die einzige
            // Pruefung, die bei Erfolg schreibt. Hier wird nur verglichen.
            zeilen.addAll(sprache.bau(ParzellenSatz.ZUSAMMENLEGEN_ZU_WENIG, 1L,
                    Einsatz.keine()));
            return List.copyOf(zeilen);
        }
        zeilen.addAll(sprache.bau(ParzellenSatz.ZUSAMMENLEGEN_BESTAETIGEN, 1L,
                nummer(px, pz).mit("richtung", richtung.wort())));
        return List.copyOf(zeilen);
    }

    void zusammenlegen(Player spieler, Richtung richtung, int gemeintX, int gemeintZ) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        if (!stehtAuf(spieler, ort, gemeintX, gemeintZ,
                ParzellenSatz.TAT_NICHTS_ZUSAMMENGELEGT)) {
            return;
        }
        // Was VOR dieser Zusammenlegung schon Gruppe war, und zwar auf beiden Seiten. Auf
        // einer alten Fuge steht inzwischen ein Haus; sie noch einmal zu fuellen wuerde das
        // Erdgeschoss auf Gras zuruecksetzen. Die Felder beider Seiten einzeln holen und
        // nicht zusammen: Vereinigt waeren sie genau die neue Gruppe, und dann gaebe es
        // nichts zu fuellen.
        Rasterplan plan = buch.plan();
        int px = plan.parzelleX(ort.getBlockX());
        int pz = plan.parzelleZ(ort.getBlockZ());
        List<List<Parzelle>> schonGelegt = List.of(
                buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ()),
                buch.gruppenFelder(welt, plan.ursprungX(px + richtung.dx()),
                        plan.ursprungZ(pz + richtung.dz())));
        var entscheid = buch.zusammenlegen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), richtung, gruppenFelder(spieler));
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());
        zusammengelegtZeilen(sprache, gruppe.size(), entscheid.kosten(),
                buch.kasse().guthaben(spieler.getUniqueId()))
                .forEach(spieler::sendMessage);
        andereDenBoden(spieler, () -> wegbau.fuelle(gruppe, schonGelegt),
                ParzellenSatz.BODEN_WEG_IST_BAULAND);
    }

    /**
     * Zusammengelegt - die Felderzahl und die Rechnung.
     *
     * <p>Die Rechnung ist {@code parzelle.bezahlt}, derselbe Satz wie nach einem Kauf.
     * Damit ist die Marke {@code HALB IN KRAFT:} auf diesem Schluessel aufgeloest: Bis zu
     * diesem Zug baute diese Methode die Worte selbst, und der Betreiber las im Spiel
     * beides nebeneinander.
     */
    static List<Component> zusammengelegtZeilen(Sprache sprache, int felder, long kosten,
                                                long guthaben) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                ParzellenSatz.ZUSAMMENLEGEN_ERTEILT, 1L,
                Einsatz.von("anzahl", String.valueOf(felder))));
        zeilen.addAll(sprache.bau(ParzellenSatz.BEZAHLT, 1L,
                rechnung(sprache, kosten, guthaben)));
        return List.copyOf(zeilen);
    }

    /**
     * Warnt vor dem Auftrennen - und nennt vorher, wie viele Bloecke dabei verschwinden.
     *
     * <p>Das ist der Grund, warum es diese Warnung ueberhaupt gibt. Nach dem Zusammenlegen
     * ist die ehemalige Wegflaeche Bauland, und der Spieler baut dort. Beim Auftrennen muss
     * der Weg zurueck, sonst stuende eine Mauer quer ueber einer oeffentlichen Strasse, die
     * niemand mehr entfernen darf. Also wird geraeumt - aber nicht, ohne es vorher zu sagen.
     */
    void warneVorAuftrennen(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        var entscheid = buch.pruefeAuftrennen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }
        if (zuSchnellHintereinander(spieler)) {
            return;
        }
        Parzelle anker = entscheid.parzelle().orElseThrow();
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());

        // Die drei Zeilen zuerst, dann die Abschaetzung: Sie liest jeden Block ueber dem
        // Weg und braucht dafuer Zeit. Wer sie vorzieht, laesst den Spieler vor leerem
        // Chat warten - es ist derselbe Grund, aus dem es die Abklingzeit darueber gibt.
        auftrennenWarnungZeilen(sprache, gruppe.size()).forEach(spieler::sendMessage);
        nenneAufwand(spieler, wegbau.aufwandBeimAuftrennen(gruppe, List.of()));
        auftrennenBestaetigenZeilen(sprache, anker.x(), anker.z())
                .forEach(spieler::sendMessage);
    }

    /**
     * Die Warnung vor dem ganzen Auftrennen - EIN Satz, drei Chatzeilen.
     *
     * <p>Die dritte Zeile traegt zwei doppelte Anfuehrungszeichen mitten im Wert. Weil
     * jeder Wert der Vorlage selbst in doppelten Anfuehrungszeichen steht, tragen sie
     * dort je einen Backslash - der einzige Satz dieses Zuges, der eine YAML-Maskierung
     * braucht.
     */
    static List<Component> auftrennenWarnungZeilen(Sprache sprache, int felder) {
        return sprache.bau(ParzellenSatz.AUFTRENNEN_ALLES_WARNUNG, 1L,
                Einsatz.von("anzahl", String.valueOf(felder)));
    }

    /** Die Bestaetigungszeile fuer das ganze Auftrennen - mit der Nummer des Ankers. */
    static List<Component> auftrennenBestaetigenZeilen(Sprache sprache, int px, int pz) {
        return sprache.bau(ParzellenSatz.AUFTRENNEN_ALLES_BESTAETIGEN, 1L,
                nummer(px, pz));
    }

    /**
     * Warnt vor dem Herausloesen des Feldes, auf dem der Spieler steht.
     *
     * <p>Zurueck kommt nur der Weg zu diesem einen Feld. Die Wege im Rest des
     * Grundstuecks bleiben Bauland - sonst muesste der Spieler sie neu bezahlen, nur weil
     * er ein Feld abgeben wollte.
     */
    void warneVorHerausloesen(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        var entscheid = buch.pruefeHerausloesen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }
        if (zuSchnellHintereinander(spieler)) {
            return;
        }
        Parzelle feld = entscheid.parzelle().orElseThrow();
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());
        List<Parzelle> bleibend = ohneDasFeld(gruppe, feld);

        // Dieselbe Reihenfolge wie beim ganzen Auftrennen: erst der Text, dann die
        // Abschaetzung, dann die Bestaetigungszeile.
        herausloesenWarnungZeilen(sprache, gruppe.size(), feld.x(), feld.z(),
                bleibend.size()).forEach(spieler::sendMessage);
        nenneAufwand(spieler, wegbau.aufwandBeimAuftrennen(gruppe, bleibt(bleibend)));
        herausloesenBestaetigenZeilen(sprache, feld.x(), feld.z())
                .forEach(spieler::sendMessage);
    }

    /**
     * Die Warnung vor dem Herausloesen - und bei einer Gruppe aus zwei Feldern die vor
     * dem ganzen Auftrennen. Zuletzt in beiden Faellen der Hinweis auf den Weg; alles,
     * was vor der Abschaetzung steht.
     *
     * @param bleibend wie viele Felder danach noch Gruppe waeren
     */
    static List<Component> herausloesenWarnungZeilen(Sprache sprache, int felder,
                                                     int feldX, int feldZ, int bleibend) {
        List<Component> zeilen = new ArrayList<>();
        if (bleibend < 2) {
            zeilen.addAll(sprache.bau(ParzellenSatz.AUFTRENNEN_FELD_WARNUNG_ZWEI, 1L,
                    Einsatz.von("anzahl", String.valueOf(felder))));
        } else {
            zeilen.addAll(sprache.bau(ParzellenSatz.AUFTRENNEN_FELD_WARNUNG, 1L,
                    nummer(feldX, feldZ).mit("anzahl", String.valueOf(bleibend))));
        }
        // Dieselbe Zeile unter BEIDEN Faellen - darum steht ihr Schluessel unter
        // auftrennen und nicht unter auftrennen.feld.
        zeilen.addAll(sprache.bau(ParzellenSatz.AUFTRENNEN_WEG_KOMMT_ZURUECK, 1L,
                Einsatz.keine()));
        return List.copyOf(zeilen);
    }

    /** Die Bestaetigungszeile fuer das Herausloesen - mit der Nummer des Feldes. */
    static List<Component> herausloesenBestaetigenZeilen(Sprache sprache, int px, int pz) {
        return sprache.bau(ParzellenSatz.AUFTRENNEN_FELD_BESTAETIGEN, 1L, nummer(px, pz));
    }

    /**
     * Ob diese Abschaetzung noch warten muss.
     *
     * <p>Gefragt wird erst, wenn feststeht, dass der Befehl ueberhaupt durchgeht: Eine
     * Absage ist billig, und wer falsch steht, soll das sofort erfahren und nicht erst
     * nach drei Sekunden.
     */
    private boolean zuSchnellHintereinander(Player spieler) {
        long rest = abschaetzungen.restNs(spieler.getUniqueId(), System.nanoTime());
        if (rest <= 0L) {
            return false;
        }
        long sekunden = (rest + 999_999_999L) / 1_000_000_000L;
        zuSchnellZeilen(sprache, sekunden).forEach(spieler::sendMessage);
        return true;
    }

    /**
     * Die Abschaetzung muss noch warten - mit der Restzeit.
     *
     * <p>{@code Art.GEZAEHLT}: Der Ternaer ueber dem Wortlaut ist weg, die Wahl zwischen
     * "Sekunde" und "Sekunden" trifft {@code Sprache.bau} an der einen Stelle.
     *
     * <p><b>Bei null oder weniger Sekunden GAR NICHTS</b> - wie {@link #aufwandZeilen} bei
     * zwei Nullen. Die Wache steht hier UND im Zweig darueber, und das ist Absicht: Der
     * Zweig entscheidet, ob der Befehl ueberhaupt wartet, diese Zeile entscheidet, was der
     * Spieler liest. Bewacht war bisher nur die erste; aus {@code rest <= 0} ein
     * {@code rest < 0} zu machen hielt jede Probe gruen, und der Spieler las "Noch 0
     * Sekunden" und wartete auf etwas, das nicht kommt. Jetzt faengt {@code SendewegTest}
     * die zweite Haelfte, und die eine Stelle traegt Wache UND Probe.
     */
    static List<Component> zuSchnellZeilen(Sprache sprache, long sekunden) {
        if (sekunden <= 0L) {
            return List.of();
        }
        return sprache.bau(ParzellenSatz.ABKLINGZEIT, sekunden,
                Einsatz.von("anzahl", String.valueOf(sekunden)));
    }

    /**
     * Nennt, was die Weltarbeit kostet - Verlust in Rot, Fuellung in Grau.
     *
     * <p>Dieselben Saetze fuer das Herausloesen und fuer das ganze Auftrennen. Zwei
     * Fassungen wuerden irgendwann verschiedene Zahlen verschieden benennen.
     */
    void nenneAufwand(Player spieler, Wegbau.Aufwand aufwand) {
        aufwandZeilen(sprache, aufwand).forEach(spieler::sendMessage);
    }

    /**
     * Die Zeilen dazu - keine, eine, zwei oder drei.
     *
     * <p><b>Bei einer Null wird GAR NICHTS gesendet</b>, und darum bleibt diese Wache ein
     * {@code if} und wird kein Ternaer ueber dem Wortlaut: Zusammengefasst stuende dem
     * Spieler "Dabei verschwinden 0 Bloecke von der Flaeche." neben "Auf der Wegflaeche
     * steht nichts".
     *
     * <p><b>Der ganze {@link Wegbau.Aufwand} kommt herein und nicht drei einzelne
     * Werte.</b> Vorher waren es zwei gleichartige {@code int} hintereinander, und ein
     * Tausch an der Aufrufstelle war fuer den Compiler in Ordnung: Der Spieler las dann
     * "Dabei verschwinden 12 Bloecke" und "4928 Fehlstellen werden wieder gefuellt" -
     * genau die Umkehrung, vor der dieses Javadoc warnt. Jetzt gibt es an der
     * Aufrufstelle nur noch einen Wert, und {@code istNichts} kommt aus demselben Satz
     * wie die Zahlen, kann ihnen also nicht widersprechen.
     */
    static List<Component> aufwandZeilen(Sprache sprache, Wegbau.Aufwand aufwand) {
        int verschwindet = aufwand.verschwindet();
        int verfuellt = aufwand.verfuellt();
        boolean istNichts = aufwand.istNichts();
        List<Component> zeilen = new ArrayList<>();
        // "von der Flaeche" und nicht "die auf der Wegflaeche stehen": Gezaehlt wird die
        // ganze Spalte, also auch ein Fremdblock im Schacht darunter. Wer den Satz woertlich
        // nimmt, sucht auf der Oberflaeche und findet nichts - und haelt die rote Zahl fuer
        // einen Fehler.
        if (verschwindet > 0) {
            zeilen.addAll(sprache.bau(ParzellenSatz.AUFWAND_VERSCHWINDET, verschwindet,
                    Einsatz.von("anzahl", String.valueOf(verschwindet))));
        }
        // Getrennt von den Verlusten und in Grau: Hier geht nichts weg, hier kommt etwas
        // hinzu. In einer Zahl zusammengefasst stuende ueber einem leeren Becken "es
        // verschwinden 4.928 Bloecke" - und das Gegenteil waere wahr. "Fehlstelle" und
        // nicht "unter der Flaeche", weil die Oberflaeche selbst dazugehoert.
        if (verfuellt > 0) {
            zeilen.addAll(sprache.bau(ParzellenSatz.AUFWAND_VERFUELLT, verfuellt,
                    Einsatz.von("anzahl", String.valueOf(verfuellt))));
        }
        if (istNichts) {
            zeilen.addAll(sprache.bau(ParzellenSatz.AUFWAND_NICHTS, 1L, Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    /** Die Felder der Gruppe ohne dieses eine. */
    private static List<Parzelle> ohneDasFeld(List<Parzelle> gruppe, Parzelle feld) {
        return gruppe.stream().filter(anderes -> anderes.id() != feld.id()).toList();
    }

    /**
     * Was nach dem Schritt noch Gruppe ist - fuer die Weltarbeit.
     *
     * <p>Unter zwei Feldern ist es keine Gruppe mehr, dann kommt der ganze Weg zurueck.
     */
    private static List<List<Parzelle>> bleibt(List<Parzelle> bleibend) {
        return bleibend.size() < 2 ? List.of() : List.of(bleibend);
    }

    /**
     * Loest das Feld heraus, auf dem der Spieler steht.
     *
     * <p>Bei einer Gruppe aus zwei Feldern trennt es ganz auf: Was uebrig bliebe, waere ein
     * einzelnes Feld, und eine Gruppe aus einem Feld gibt es nicht.
     */
    void herausloesen(Player spieler, int gemeintX, int gemeintZ) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        if (!stehtAuf(spieler, ort, gemeintX, gemeintZ,
                ParzellenSatz.TAT_NICHTS_HERAUSGELOEST)) {
            return;
        }
        // Die Felder VOR dem Loesen holen, wie beim ganzen Auftrennen: Danach sagt die
        // Ablage nicht mehr, welche Flaeche zurueckzubauen waere.
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());
        var entscheid = buch.herausloesen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }
        Parzelle feld = entscheid.parzelle().orElseThrow();
        List<Parzelle> bleibend = ohneDasFeld(gruppe, feld);
        herausgeloestZeilen(sprache, gruppe.size(), feld.x(), feld.z(), bleibend.size())
                .forEach(spieler::sendMessage);
        andereDenBoden(spieler, () -> wegbau.baueZurueck(gruppe, bleibt(bleibend)),
                ParzellenSatz.BODEN_WEG_IST_ZURUECK);
    }

    /**
     * Herausgeloest - oder, bei einer Gruppe aus zwei Feldern, ganz aufgetrennt.
     *
     * <p>Der Wortlaut des zweiten Falles steht nicht zweimal im Quelltext: Er ist genau
     * derselbe wie beim ganzen Auftrennen und kommt deshalb von dort.
     *
     * @param bleibend wie viele Felder jetzt noch Gruppe sind
     */
    static List<Component> herausgeloestZeilen(Sprache sprache, int felder, int feldX,
                                               int feldZ, int bleibend) {
        if (bleibend < 2) {
            return aufgetrenntZeilen(sprache, felder);
        }
        return sprache.bau(ParzellenSatz.AUFTRENNEN_FELD_ERTEILT, 1L,
                nummer(feldX, feldZ).mit("anzahl", String.valueOf(bleibend)));
    }

    void auftrennen(Player spieler, int gemeintX, int gemeintZ) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        if (!stehtAuf(spieler, ort, gemeintX, gemeintZ,
                ParzellenSatz.TAT_NICHTS_AUFGETRENNT)) {
            return;
        }
        // Die Felder VOR dem Auftrennen holen. Danach gehoert keines mehr zu einer Gruppe,
        // und welche Flaeche zurueckzubauen waere, liesse sich nicht mehr feststellen.
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());
        var entscheid = buch.auftrennen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }
        aufgetrenntZeilen(sprache, gruppe.size()).forEach(spieler::sendMessage);
        andereDenBoden(spieler, () -> wegbau.baueZurueck(gruppe, List.of()),
                ParzellenSatz.BODEN_WEG_IST_ZURUECK);
    }

    /** Aufgetrennt - aus der Gruppe sind wieder eigene Parzellen geworden. */
    static List<Component> aufgetrenntZeilen(Sprache sprache, int felder) {
        return sprache.bau(ParzellenSatz.AUFTRENNEN_ERTEILT, 1L,
                Einsatz.von("anzahl", String.valueOf(felder)));
    }

    /**
     * Fuehrt die Bodenarbeit aus und sagt, was daraus geworden ist.
     *
     * <p>Die Ablage ist an dieser Stelle schon geaendert, und beides zusammen laesst sich
     * nicht sichern - eine Welt kennt keine Transaktion. Scheitert das Setzen, gilt
     * trotzdem die Ablage: Der Spieler darf dort bauen, es sieht nur noch nicht danach aus.
     * Das ist die harmlose Haelfte des Fehlers, und sie gehoert ins Protokoll, nicht in
     * einen Abbruch - andersherum waere das Geld weg und das Grundstueck nicht da.
     */
    private void andereDenBoden(Player spieler, IntSupplier arbeit, Satz gelungen) {
        // Das Wortstueck VOR dem try holen: Es kommt aus der Sprachdatei und hat mit dem
        // Gelingen der Weltarbeit nichts zu tun.
        String wort = sprache.wort(gelungen, Einsatz.keine());
        try {
            // Die Weltarbeit bleibt IM try: Scheitert sie, darf keine Erfolgszeile
            // gebaut und keine gesendet werden.
            bodenZeilen(sprache, wort, arbeit.getAsInt())
                    .forEach(spieler::sendMessage);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE,
                    "Der Boden der Gruppe liess sich nicht aendern - die Ablage stimmt, "
                            + "die Welt nicht", fehler);
            bodenFehlerZeilen(sprache).forEach(spieler::sendMessage);
        }
    }

    /**
     * Was aus der Bodenarbeit geworden ist.
     *
     * <p>"geaendert" und nicht nur die Zahl: Darin stecken der Weg selbst und das, was
     * geraeumt wurde. Ohne das Wort liest sich "Der Weg ist zurueck (623 Bloecke)", als
     * haette der Weg 623 Bloecke.
     *
     * @param gelungen was gelungen ist - das Wortstueck, das der Zweig mitgibt
     */
    static List<Component> bodenZeilen(Sprache sprache, String gelungen, int geaendert) {
        return sprache.bau(ParzellenSatz.BODEN_GELUNGEN, 1L,
                Einsatz.von("gelungen", gelungen)
                        .mit("anzahl", String.valueOf(geaendert)));
    }

    /** Die Ablage stimmt, die Welt nicht. */
    static List<Component> bodenFehlerZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.BODEN_FEHLER, 1L, Einsatz.keine());
    }

    // --- Kleinteile ------------------------------------------------------------

    /**
     * Ob der Spieler auf der Rasterzelle steht, die im Befehl steht.
     *
     * <p>Die Sicherung gegen einen Schritt zwischen Warnung und Bestaetigung, fuer alle
     * drei Befehle, die etwas Endgueltiges tun. Sie steht an einer Stelle, weil der dritte
     * sie sonst irgendwann nicht bekommt.
     *
     * <p>Gefragt wird nach der Rasterzelle und nicht nach dem zustaendigen Besitz: Beide
     * nennen dieselbe Nummer - {@code zustaendigerBesitz} sucht die Zeile ja bei
     * {@code parzelleX/parzelleZ} -, aber die Rasterzelle braucht keine Abfrage.
     */
    private boolean stehtAuf(Player spieler, Location ort, int gemeintX, int gemeintZ,
                             Satz tat) {
        Rasterplan plan = buch.plan();
        if (plan.parzelleX(ort.getBlockX()) == gemeintX
                && plan.parzelleZ(ort.getBlockZ()) == gemeintZ) {
            return true;
        }
        // Die Tat ist ein WERT aus der Sprachdatei: Vier Zweige geben vier verschiedene
        // mit, und der Satz darum steht nur EINMAL in der Datei.
        stehtNichtAufZeilen(sprache, gemeintX, gemeintZ,
                sprache.wort(tat, Einsatz.keine())).forEach(spieler::sendMessage);
        return false;
    }

    /**
     * Er steht auf einer anderen Zelle, als der Befehl nennt.
     *
     * @param nichtsPassiert was dadurch nicht geschehen ist - das Wortstueck des Zweiges
     */
    static List<Component> stehtNichtAufZeilen(Sprache sprache, int gemeintX, int gemeintZ,
                                               String nichtsPassiert) {
        return sprache.bau(ParzellenSatz.STEHT_NICHT_AUF, 1L,
                nummer(gemeintX, gemeintZ).mit("tat", nichtsPassiert));
    }

    /** Ob dieser Ort in der Marktstadt liegt - der eine Vergleich dafuer. */
    private boolean inDerMarktstadt(Location ort) {
        return marktstadt.equals(ort.getWorld());
    }

    /**
     * Die Absage an den Spieler: der Satz - und wo ein RANG die Grenze setzt, die Werbung
     * dahinter.
     *
     * <p>Beides hier und nicht an den zehn Aufrufstellen. Ein Hinweis, den neun von zehn
     * Wegen kennen, wirkt auf den zehnten wie ein Zufall, und der Spieler sucht beim
     * naechsten Mal vergeblich danach.
     *
     * <p>Angehaengt wird nur, wo der Rang wirklich der Grund ist. Wer zu wenig Geld hat
     * oder auf dem Weg steht, bekommt keine Werbung - sie wuerde ihm etwas verkaufen
     * wollen, das sein Problem nicht loest.
     */
    void absage(Player spieler, Besitzbuch.Bescheid bescheid) {
        boolean amLimit = anzahl(spieler) >= hoechstzahl(spieler);
        absageZeilen(sprache, bescheid, amLimit, hoechstzahl(spieler),
                gruppenFelder(spieler), wirtschaft.werte().rangWerbung())
                .forEach(spieler::sendMessage);
    }

    /**
     * Der Satz zum Bescheid - und dahinter die Werbung, wo ein Rang der Grund ist.
     *
     * <p>Beide Zahlen kommen herein, obwohl jeder Bescheid hoechstens eine braucht: Welche
     * Zahl in welchen Satz geht, entscheidet der {@code switch} hier - an einer Stelle,
     * die eine Probe unmittelbar aufrufen kann, und nicht am Sendeort.
     *
     * @param amLimit      ob er seine Hoechstzahl schon erreicht hat
     * @param hoechstzahl  wie viele Parzellen sein Rang erlaubt
     * @param gruppenfelder wie viele Felder eine Gruppe haben darf
     * @param rangWerbung  die Zeile aus der Konfiguration, moeglicherweise leer
     */
    static List<Component> absageZeilen(Sprache sprache, Besitzbuch.Bescheid bescheid,
                                        boolean amLimit, int hoechstzahl,
                                        int gruppenfelder, String rangWerbung) {
        // Der switch ueber den WORTLAUT ist weg. Was bleibt, ist die Frage, WELCHE Zahl
        // dieser Bescheid nennt - und die beantwortet das Verzeichnis, nicht dieser
        // Zweig. Zwei Verzweigungen ueber dieselbe Frage laufen irgendwann auseinander.
        long anzahl = switch (Sprachschluessel.zahlquelle(bescheid, amLimit)) {
            case HOECHSTZAHL -> hoechstzahl;
            case GRUPPENFELDER -> gruppenfelder;
            // Kein Satz nennt hier eine Zahl. Die 1 steht fuer die Einzahlform, die
            // Art.SATZ ohnehin nicht kennt - gewaehlt wird nichts.
            case KEINE -> 1L;
        };
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                Sprachschluessel.bescheid(bescheid, amLimit), anzahl,
                Einsatz.von("anzahl", String.valueOf(anzahl))));
        // Die Wache bleibt am Sendeort (Bauplan 5): Angehaengt wird nur, wo der Rang
        // wirklich der Grund ist. Wer zu wenig Geld hat, bekommt keine Werbung - sie
        // wuerde ihm etwas verkaufen wollen, das sein Problem nicht loest.
        if (bescheid == Besitzbuch.Bescheid.GRENZE_ERREICHT
                || bescheid == Besitzbuch.Bescheid.GRUPPE_ZU_GROSS
                || (bescheid == Besitzbuch.Bescheid.NOCH_FREI && amLimit)) {
            zeilen.addAll(werbungZeilen(sprache, rangWerbung));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die Zeile aus der Konfiguration - einmal, wenn sie gesetzt ist, sonst keine.
     *
     * <p>In Gelb und nicht in Rot: Die Absage darueber ist die Nachricht, das hier ist
     * das Angebot. Zwei rote Zeilen hintereinander liest niemand als zwei Dinge.
     *
     * <p>Die EINE Stelle, an der diese Zeile entsteht - vier Zweige haengen sie an, und
     * jeder von ihnen tut es ueber diese Methode. Ein Hinweis, den drei von vier Wegen
     * kennen, wirkt auf den vierten wie ein Zufall.
     */
    static List<Component> werbungZeilen(Sprache sprache, String rangWerbung) {
        if (rangWerbung.isEmpty()) {
            return List.of();
        }
        // Nur die FARBE kommt aus der Sprachdatei; der Satz selbst steht in der
        // config.yml unter hinweise.rang-werbung und ist eine Einstellung dieses Servers.
        return sprache.bau(ParzellenSatz.RANG_WERBUNG, 1L,
                Einsatz.von("werbung", rangWerbung));
    }

    /** Die Preise, die JETZT gelten - beim Gebrauch geholt, nie in ein Feld kopiert. */
    private Preisstaffel preise() {
        return wirtschaft.werte().preise();
    }

    /**
     * Wo der Spieler steht - aber nur, wenn er in der Marktstadt steht.
     *
     * <p>Das ist bewusst die EINZIGE Stelle, an der ein Befehl an Koordinaten kommt. Vorher
     * war die Weltpruefung eine freiwillige Zeile am Anfang jeder Methode, und zwei von
     * sieben hatten sie nicht: Wer in einer anderen Welt stand, veraenderte damit die
     * Marktstadt-Parzelle an genau diesen Koordinaten - fremden Besitz, aus einer Welt
     * heraus, in der es keine Parzellen gibt. Jetzt gibt es ohne diese Pruefung keinen Ort,
     * mit dem sich etwas anstellen liesse.
     */
    private Optional<Location> ortInMarktstadt(Player spieler) {
        Location ort = spieler.getLocation();
        if (inDerMarktstadt(ort)) {
            return Optional.of(ort);
        }
        nichtInDerMarktstadtZeilen(sprache).forEach(spieler::sendMessage);
        return Optional.empty();
    }

    /** Parzellen gibt es nur in der einen Welt. */
    static List<Component> nichtInDerMarktstadtZeilen(Sprache sprache) {
        return sprache.bau(ParzellenSatz.NICHT_IN_DER_MARKTSTADT, 1L, Einsatz.keine());
    }

    private int anzahl(Player spieler) {
        return buch.parzellenVon(welt, spieler.getUniqueId()).size();
    }

    /**
     * Wie viele Parzellen dieser Spieler haben darf.
     *
     * <p>Ueber Rechte und nicht ueber eine Rangtabelle im Plugin: So legt die
     * Rechteverwaltung die Raenge fest - dieselbe, die auch alles andere regelt - und
     * Freimarken muss von Raengen nichts wissen.
     *
     * <p>Ein Operator bekommt dadurch NICHT von sich aus mehr Parzellen: Gelesen werden die
     * gesetzten Rechte, und {@code freimarken.parzellen.8} ist bei niemandem gesetzt, nur
     * weil er Operator ist. Wer mehr haben soll, bekommt das Recht eingetragen. So gewollt -
     * die Obergrenze ist eine Spielregel und keine Verwaltungsbefugnis.
     */
    private int hoechstzahl(Player spieler) {
        return ausRecht(spieler, RECHT_ANZAHL, RECHT_EXTRA_ANZAHL,
                wirtschaft.werte().hoechstzahl());
    }

    /**
     * Eine Grenze aus den Rechten: die hoechste erlaubte Zahl plus den Zuschlag.
     *
     * <p>Eine Methode fuer beide Grenzen, damit der Zuschlag nicht an einer von zwei
     * Stellen vergessen werden kann. Genau das ist die Art Fehler, die niemand meldet:
     * Der Spieler sieht nur, dass seine Mitgliedschaft beim Zusammenlegen nichts bringt,
     * und haelt es fuer die Regel.
     */
    private static int ausRecht(Player spieler, String recht, String zuschlagsrecht,
                                int vorgabe) {
        return mitZuschlag(hoechsteAus(spieler, recht, vorgabe),
                hoechsteAus(spieler, zuschlagsrecht, 0));
    }

    /**
     * Grundlage plus Zuschlag, ohne ueberzulaufen.
     *
     * <p>Beide Zahlen koennen bis {@link Integer#MAX_VALUE} gehen - die Auswertung der
     * Rechte kappt jede einzelne dort. Zusammengezaehlt liefe ein {@code int} ueber und
     * waere danach NEGATIV, und eine negative Obergrenze heisst: gar keine Parzelle mehr,
     * ausgerechnet fuer den, der am meisten hat.
     */
    static int mitZuschlag(int grundlage, int zuschlag) {
        return (int) Math.min((long) grundlage + Math.max(zuschlag, 0), Integer.MAX_VALUE);
    }

    /**
     * Wie viele Felder eine Gruppe dieses Spielers haben darf.
     *
     * <p>Eine eigene Grenze und bewusst nicht dieselbe wie {@link #hoechstzahl}: Wie viele
     * Parzellen jemand besitzt, bremst das Flaechenhorten; wie viele davon er zu EINEM
     * Grundstueck verbinden darf, entscheidet, wie gross ein einzelnes Bauwerk werden kann.
     */
    private int gruppenFelder(Player spieler) {
        return ausRecht(spieler, RECHT_GRUPPE, RECHT_EXTRA_GRUPPE,
                wirtschaft.werte().gruppenFelder());
    }

    /**
     * Die hoechste Zahl, die ein gesetztes Recht mit diesem Anfang nennt - mindestens die
     * Vorgabe.
     *
     * <p>Eine Auswertung fuer beide Grenzen. Zwei Abschriften derselben Schleife waeren
     * zwei Orte fuer dieselbe Falle, und die zweite bekaeme den Hinweis unten nicht mit.
     */
    private static int hoechsteAus(Player spieler, String recht, int vorgabe) {
        int hoechste = vorgabe;
        for (PermissionAttachmentInfo eintrag : spieler.getEffectivePermissions()) {
            if (!eintrag.getValue() || !eintrag.getPermission().startsWith(recht)) {
                continue;
            }
            try {
                // long und dann gekappt: Integer.parseInt haette auch bei einer gueltigen,
                // nur sehr grossen Zahl geworfen - und die waere dann wie ein Tippfehler
                // behandelt worden, also stillschweigend als "keine Erhoehung".
                long gewuenscht = Long.parseLong(
                        eintrag.getPermission().substring(recht.length()));
                hoechste = (int) Math.max(hoechste, Math.min(gewuenscht, Integer.MAX_VALUE));
            } catch (NumberFormatException keineZahl) {
                // "freimarken.parzellen.alle" oder ein Tippfehler - kein Grund fuer einen
                // Abbruch, nur kein Anlass, die Obergrenze zu heben.
            }
        }
        return hoechste;
    }

    private static String namen(UUID spieler) {
        String name = Bukkit.getOfflinePlayer(spieler).getName();
        return name != null ? name : spieler.toString().substring(0, 8);
    }

    /**
     * Die Namen als Aufzaehlung - und bei einer leeren Sammlung NICHTS.
     *
     * <p>Hier stand {@code orElse("niemand")}, und damit stand das Wort an zwei Orten: Die
     * Vorlage fuehrt es als {@code parzelle.mitbauer.niemand}, und {@code mitbauerZeilen}
     * holt es dort. Erreichbar war das Literal nicht - beide Aufrufstellen pruefen vorher
     * {@code isEmpty()} -, aber eine kuenftige dritte haette "niemand" gelesen, waehrend
     * die Datei "keiner" sagt. Jetzt gibt es nur eine Quelle fuer dieses Wort, und eine
     * vergessene Wache faellt als leere Stelle im Satz auf, statt stumm richtig zu sein.
     */
    private static String namenListe(Collection<UUID> spieler) {
        return spieler.stream().map(ParzellenBefehle::namen).sorted().reduce(
                (a, b) -> a + ", " + b).orElse("");
    }

    /**
     * Sucht einen Spieler am Namen - ohne ins Netz und ohne durch die Historie zu laufen.
     *
     * <p>Absichtlich NICHT {@code Bukkit.getOfflinePlayer(String)}: Das fragt im Zweifel bei
     * Mojang nach und haelt dabei den ganzen Server an.
     *
     * <p>Und absichtlich nicht mehr {@code Bukkit.getOfflinePlayers()}: Das baute die Liste
     * aller je angemeldeten Spieler bei JEDEM Aufruf neu auf, mit einem {@code getName()}
     * je Eintrag. Ein Spieler, der diesen Befehl mit Zufallsnamen wiederholt, haelt damit
     * den ganzen Server an - ohne Recht, ohne eigene Parzelle, aus jeder Welt heraus.
     * {@code getOfflinePlayerIfCached} schaut nur in den Zwischenspeicher und geht nie ins
     * Netz; der Preis ist, dass ein lange abwesender Spieler nicht gefunden wird, solange
     * ihn niemand nachgeschlagen hat.
     */
    private static Optional<UUID> findeSpieler(String name) {
        Player anwesend = Bukkit.getPlayerExact(name);
        if (anwesend != null) {
            return Optional.of(anwesend.getUniqueId());
        }
        OfflinePlayer bekannt = Bukkit.getOfflinePlayerIfCached(name);
        return bekannt != null ? Optional.of(bekannt.getUniqueId()) : Optional.empty();
    }

    /** Zum Eintragen: wer gerade da ist. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> anwesendeNamen() {
        return Commands.argument("spieler", StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    Bukkit.getOnlinePlayers().forEach(p -> bauer.suggest(p.getName()));
                    return bauer.buildFuture();
                });
    }

    /**
     * Zum Austragen: wer auf dieser Parzelle steht.
     *
     * <p>Ohne Meldung und ohne {@link #ortInMarktstadt}: Ein Vorschlagsgeber laeuft bei
     * jedem Tastendruck. Eine Absage im Chat waere dort kein Hinweis, sondern Laerm.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> mitbauerNamen() {
        return Commands.argument("spieler", StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    if (zusammenhang.getSource().getExecutor() instanceof Player spieler) {
                        Location ort = spieler.getLocation();
                        if (inDerMarktstadt(ort)) {
                            buch.zustaendigerBesitz(welt, ort.getBlockX(), ort.getBlockZ())
                                    .ifPresent(parzelle -> parzelle.mitbauer().stream()
                                            .map(ParzellenBefehle::namen)
                                            .sorted()
                                            .forEach(bauer::suggest));
                        }
                    }
                    return bauer.buildFuture();
                });
    }

    /** Hebt den Spieler aus der Befehlsquelle - alle Zweige hier sind spielerpflichtig. */
    private Command<CommandSourceStack> fuerSpieler(Consumer<Player> tat) {
        return zusammenhang -> {
            if (zusammenhang.getSource().getExecutor() instanceof Player spieler) {
                tat.accept(spieler);
            }
            return Command.SINGLE_SUCCESS;
        };
    }

    /**
     * Wie {@link #fuerSpieler}, zusaetzlich mit Ort und dem eingegebenen Namen.
     *
     * <p>Die Welt wird hier geprueft, VOR allem anderen. Vorher lief die Namensaufloesung
     * zuerst - und damit auch dann, wenn der Spieler in einer Welt stand, in der es keine
     * Parzellen gibt. Der Name wird absichtlich unaufgeloest weitergegeben: Eintragen und
     * Austragen suchen an verschiedenen Stellen.
     */
    private int mitOrtUndName(CommandContext<CommandSourceStack> zusammenhang, OrtUndName tat) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        tat.fuehreAus(spieler, hier.get(),
                StringArgumentType.getString(zusammenhang, "spieler"));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Wie {@link #fuerSpieler}, zusaetzlich mit der Nummer aus dem Befehl.
     *
     * <p>Die EINE Stelle, an der die zwei Zahlen der Bestaetigung aus Brigadier geholt
     * werden - vier Zweige brauchen sie, und jeder holte sie bis zum 04.11.2026 selbst.
     * Dass die Zweige dahinter nur noch zwei {@code int} nehmen, ist der Zweck: Ein Zweig
     * ohne {@code CommandContext} laesst sich von einer Probe aufrufen, und damit ist
     * pruefbar, was er sendet.
     */
    private int mitNummer(CommandContext<CommandSourceStack> zusammenhang, MitNummer tat) {
        if (zusammenhang.getSource().getExecutor() instanceof Player spieler) {
            tat.fuehreAus(spieler, IntegerArgumentType.getInteger(zusammenhang, "x"),
                    IntegerArgumentType.getInteger(zusammenhang, "z"));
        }
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface OrtUndName {
        void fuehreAus(Player spieler, Location ort, String name);
    }

    @FunctionalInterface
    private interface MitNummer {
        void fuehreAus(Player spieler, int gemeintX, int gemeintZ);
    }
}
