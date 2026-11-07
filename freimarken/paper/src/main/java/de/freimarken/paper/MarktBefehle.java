package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Verkaufsregel;
import de.freimarken.kern.Wirtschaftsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Der Befehl {@code /markt} - das Sortiment ansehen und verkaufen.
 *
 * <p><b>Kein Wort steht hier.</b> Jede Zeile kommt aus {@link Markttext}, ihr Wortlaut aus
 * {@code sprache-vorlage.yml} und ihr Vertrag aus {@link MarktSatz} - derselbe Griff wie
 * bei {@code BerufeBefehle} und {@code ParzellenBefehle}.
 *
 * <p><b>Jede Rechnung faellt in {@link Verkaufsregel}</b>, also im Kern und ohne Bukkit.
 * Hier steht nur, was danach zu tun ist: aus der Hand nehmen, buchen, senden. Diese Datei
 * hat keine einzige Bedingung ueber Preise, Stueckzahlen oder Deckel.
 *
 * <p><b>Der Bundesmarkt ist ein ORT</b> (Plan 2.1: er steht in Altfurt). Wer nicht dort
 * ist, bekommt eine Absage - "Städte als Mittelpunkt" ist eine Entwurfsregel des Plans,
 * und ein Markt, der von ueberall aus funktioniert, ist kein Ort, sondern ein Menue.
 * Geprueft wird die WELT und kein Umkreis: Der Platz ist an einem eingemessenen Gelaende
 * festgemacht, und eine zweite Koordinate hier waere eine, die mit jenem auseinanderlaeuft.
 * Eine spaetere Verengung auf den Platz ist dann ein Eintrag, kein Umbau.
 *
 * <p><b>Die Reihenfolge: erst buchen, dann aus der Hand nehmen.</b> Umgekehrt haette eine
 * zerrissene Verbindung einen Spieler ohne Ware UND ohne Geld. So bleibt im schlimmsten
 * Fall eine Gutschrift im Buch, der die Ware fehlt - und die ist lesbar. Dieselbe
 * Richtung wie beim Parzellenkauf, nur andersherum gedacht: Dort wird zuerst abgebucht,
 * weil der Spieler etwas BEKOMMT; hier bekommt er Geld, also wird zuerst gutgeschrieben.
 *
 * <p><b>Nur die HAND wird verkauft, nicht das Inventar.</b> Ein {@code /markt verkaufen
 * alles} waere eine Buchung je Material - bei vollem Inventar ein Dutzend Transaktionen
 * in einem Tick, und das ist genau die Massenbuchung, gegen die {@code Spielzeitbuch}
 * seine Sperre haelt. Es kommt mit seinem eigenen Schritt, wenn es gesammelt bucht.
 */
final class MarktBefehle {

    /** Wer am Bundesmarkt verkaufen darf - jeder. */
    static final String RECHT = "freimarken.markt";

    /** Das Wort, unter dem der Befehl steht. */
    static final String ARGUMENT = "markt";

    private final Wirtschaftsstand wirtschaft;
    private final Kasse kasse;
    private final Clock uhr;
    private final Sprache sprache;
    private final Logger protokoll;

    /**
     * In welcher Welt der Bundesmarkt steht - der Weltschluessel.
     *
     * <p>Als Naht hereingegeben und nicht selbst gesucht: Welche Welt die Hauptstadt ist,
     * steht in {@code reisen.ankunftswelt} und wird nur beim Start gelesen. So haengt
     * dieser Befehl an derselben Entscheidung wie die Ankunft, und nicht an einer zweiten
     * Abschrift davon.
     */
    private final String weltschluessel;

    MarktBefehle(Wirtschaftsstand wirtschaft, Kasse kasse, Clock uhr, Sprache sprache,
                 String weltschluessel, Logger protokoll) {
        if (wirtschaft == null || kasse == null || uhr == null || sprache == null
                || protokoll == null) {
            throw new IllegalArgumentException("Wirtschaftsstand, Kasse, Uhr, Sprache und"
                    + " Protokoll sind Pflicht");
        }
        if (weltschluessel == null || weltschluessel.isBlank()) {
            throw new IllegalArgumentException("Ohne Weltschluessel waere der Bundesmarkt"
                    + " kein Ort, sondern ein Menue - und von jeder Welt aus offen");
        }
        this.wirtschaft = wirtschaft;
        this.kasse = kasse;
        this.uhr = uhr;
        this.sprache = sprache;
        this.weltschluessel = weltschluessel;
        this.protokoll = protokoll;
    }

    /** Der Befehlsbaum - zwei Zweige, und beide brauchen einen Spieler. */
    LiteralCommandNode<CommandSourceStack> markt() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(zusammenhang -> rufe(zusammenhang, this::zeigeListe))
                .then(Commands.literal("liste")
                        .executes(zusammenhang -> rufe(zusammenhang, this::zeigeListe)))
                .then(Commands.literal("verkaufen")
                        .executes(zusammenhang -> rufe(zusammenhang, this::verkaufe)))
                .build();
    }

    /** Was ein Zweig mit einem Spieler tut. */
    private interface Zweig {
        void fuehre(Player wer);
    }

    /**
     * Der gemeinsame Hals: Spieler holen, Welt pruefen, Zweig rufen.
     *
     * <p>Die WELTPRUEFUNG steht hier und nicht in jedem Zweig - sonst vergisst sie einer,
     * und dann ginge ein Verkauf von ueberall. Die Liste darf man dagegen ueberall
     * ansehen: Sie nennt nur Preise, und wer wissen will, ob sich der Weg lohnt, soll
     * nicht erst hingehen muessen.
     */
    private int rufe(CommandContext<CommandSourceStack> zusammenhang, Zweig zweig) {
        if (!(zusammenhang.getSource().getSender() instanceof Player wer)) {
            // Die Konsole hat keine Hand und steht in keiner Welt. Eine eigene Zeile
            // braucht das nicht: Wer auf der Konsole tippt, ist der Betreiber.
            zusammenhang.getSource().getSender().sendMessage(
                    Component.text("Der Bundesmarkt braucht einen Spieler."));
            return Command.SINGLE_SUCCESS;
        }
        zweig.fuehre(wer);
        return Command.SINGLE_SUCCESS;
    }

    private void zeigeListe(Player wer) {
        sende(wer, Markttext.listeZeilen(sprache, werte(), heuteSchon(wer)));
    }

    /**
     * Verkauft, was in der Hand ist.
     *
     * <p>Die Reihenfolge der Absagen ist die der Wahrscheinlichkeit UND der Kosten:
     * falsche Welt (ein Vergleich), leere Hand (ein Blick), kein Ankauf (ein Nachschlag),
     * zu wenig fuer eine Mark (eine Rechnung), Deckel erreicht (eine Abfrage).
     */
    private void verkaufe(Player wer) {
        if (!wer.getWorld().key().asString().equals(weltschluessel)) {
            sende(wer, Markttext.absageZeilen(sprache, MarktSatz.ABSAGE_FALSCHE_WELT,
                    null));
            return;
        }
        ItemStack inDerHand = wer.getInventory().getItemInMainHand();
        // Der Vergleich gegen die Konstante und NICHT isAir(): Jenes schlaegt in der
        // Registry nach und wirft ohne laufenden Server - gemessen gegen das echte Jar.
        // Damit waere dieser Zweig der einzige ungepruefte des Schrittes. Ein
        // Enum-Vergleich kostet nichts und sagt dasselbe.
        if (inDerHand.getAmount() <= 0 || inDerHand.getType() == Material.AIR) {
            sende(wer, Markttext.absageZeilen(sprache, MarktSatz.ABSAGE_LEERE_HAND,
                    null));
            return;
        }
        String material = inDerHand.getType().name().toLowerCase(Locale.ROOT);
        Marktwerte werte = werte();
        if (werte.ankauf().pfennige(material) <= 0L) {
            sende(wer, Markttext.absageZeilen(sprache, MarktSatz.ABSAGE_KAUFT_NICHT,
                    material));
            return;
        }

        long heuteSchon = heuteSchon(wer);
        Verkaufsregel.Verkauf verkauf = Verkaufsregel.rechne(werte, material,
                inDerHand.getAmount(), heuteSchon);
        if (verkauf.nichts()) {
            // ZWEI Gruende fuer dasselbe Nichts, und sie verlangen verschiedene Taten:
            // Am Deckel hilft Warten, bei zu wenig Stueck hilft mehr sammeln.
            boolean amDeckel = heuteSchon >= werte.tagesdeckel();
            sende(wer, Markttext.absageZeilen(sprache,
                    amDeckel ? MarktSatz.ABSAGE_DECKEL : MarktSatz.ABSAGE_ZU_WENIG,
                    material));
            return;
        }

        // Erst buchen, dann aus der Hand nehmen - siehe Klassenkopf.
        try {
            kasse.gutschreiben(wer.getUniqueId(), verkauf.mark(),
                    Buchungsgrund.BUNDESMARKT_VERKAUF);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Verkauf am Bundesmarkt nicht buchbar fuer "
                    + wer.getName() + " - " + verkauf.stuecke() + " x " + material
                    + " bleiben in der Hand.", fehler);
            sende(wer, Markttext.absageZeilen(sprache, MarktSatz.ABSAGE_NICHT_BUCHBAR,
                    null));
            return;
        }
        inDerHand.setAmount(inDerHand.getAmount() - verkauf.stuecke());
        sende(wer, Markttext.verkauftZeilen(sprache, material, verkauf));
    }

    /** Die Marktwerte, die JETZT gelten. */
    private Marktwerte werte() {
        return wirtschaft.werte().markt();
    }

    /**
     * Was dieser Spieler heute schon aus Verkaeufen bekommen hat.
     *
     * <p>Aus dem BUCHUNGSBUCH und nicht aus einem Zaehler: Dieselbe Entscheidung wie beim
     * Verdienstdeckel, und mit demselben Grund - ein Zaehler im Arbeitsspeicher
     * ueberlebte keinen Neustart, und dann waere der Deckel nach jedem Neustart offen.
     *
     * <p>Gefragt wird je Befehl und nicht je Tick: Ein Verkauf ist eine Tat des Spielers,
     * und die kostet eine Abfrage.
     */
    private long heuteSchon(Player wer) {
        return kasse.summeSeit(wer.getUniqueId(),
                Tagesgrenze.tagesbeginn(Tagesgrenze.tagVon(uhr.instant(),
                                wirtschaft.werte().zeit().zone()),
                        wirtschaft.werte().zeit().zone()),
                Buchungsgrund.BUNDESMARKT_VERKAUF);
    }

    private void sende(Player wer, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            wer.sendMessage(zeile);
        }
    }
}
