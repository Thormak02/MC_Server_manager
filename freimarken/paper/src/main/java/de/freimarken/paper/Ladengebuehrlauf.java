package de.freimarken.paper;

import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Die TAEGLICHE Ladengebuehr der Kistenshops in einer Freistadt [Teil E6].
 *
 * <p>Die zweite Haelfte der Ladengebuehr: Die erste faellt einmalig beim Anlegen (in
 * {@code Shopbuch.anlege}), diese jeden Tag. Beide gehen in die Stadtkasse, beide sind
 * getrennt einstellbar, und beide rechnen ueber dieselbe Stelle im Kern
 * ({@code Freistadtbuch.erhebeLadengebuehr}).
 *
 * <p><b>AB WERK 0 und damit still</b>, und das ist im Gegensatz zur Anlagegebuehr
 * Absicht - die Begruendung steht bei {@code Freistadtwerte.VORGABE_LADENGEBUEHR_TAG}:
 * Eine Tagesgebuehr hat eine Folge, die eine einmalige nicht hat. WER SIE NICHT ZAHLEN
 * KANN, VERLIERT SEINEN SHOP. Ob ein Server das will, entscheidet sein Betreiber.
 *
 * <p><b>Der Shop wird ABGEBAUT, die Ware bleibt in der Kiste</b> - genau wie bei jedem
 * anderen Abbau ({@code Shopbuch.baueAb}). Das ist der mildeste Weg, der die Gebuehr
 * trotzdem durchsetzt: Der Haendler verliert sein Schild und nicht seine Waren, und er
 * kann ein neues setzen, sobald er zahlen kann. Die Ladenmiete der Markthalle raeumt
 * dagegen ins Abholfach, weil dort der ZUGANG verloren geht; an einer eigenen Kiste
 * nicht.
 *
 * <p><b>Kein eigener Zeitgeber</b> und ein billiger Vortest davor - wie {@link Mietlauf},
 * {@link Truhenlauf} und {@link Verfallslauf}. Der Minutentakt schlaegt 1.440 Mal am
 * Tag, und dieser Lauf geht ueber ALLE Kistenshops.
 */
final class Ladengebuehrlauf {

    private final Shopbuch shopbuch;
    private final Shopablage shops;
    private final Freistadtbuch buch;
    private final String weltschluessel;
    private final Clock uhr;
    private final Wirtschaftsstand wirtschaft;
    private final Server server;
    private final Sprache sprache;
    private final Logger protokoll;

    /**
     * An welchem Tag der Lauf zuletzt DURCH war - nur im Arbeitsspeicher.
     *
     * <p>Dieselbe Bauart und dieselbe Begruendung wie bei {@code Mietlauf.zuletzt}: Eine
     * Spalte waere eine zweite Wahrheit ueber "heute bezahlt", und die erste steht im
     * BUCHUNGSBUCH. Gesetzt erst nach einem stoerungsfreien Lauf.
     *
     * <p><b>Der Waechter gegen eine doppelte Abbuchung ist NICHT dieses Feld</b>, und
     * das ist wichtig: Es ueberlebt keinen Neustart. Die Wahrheit ist die Buchung selbst
     * - {@code summeSeit(besitzer, tagesbeginn, FREISTADT_LADENGEBUEHR) == 0}. Auf
     * {@code == 0} und nicht auf ein Vorzeichen: Eine AUSGABE steht im Buch NEGATIV,
     * also waere ein Vorzeichenvergleich immer falsch oder immer wahr.
     */
    private LocalDate zuletzt;

    Ladengebuehrlauf(Shopbuch shopbuch, Shopablage shops, Freistadtbuch buch,
            String weltschluessel, Clock uhr, Wirtschaftsstand wirtschaft, Server server,
            Sprache sprache, Logger protokoll) {
        if (shopbuch == null || shops == null || buch == null) {
            throw new IllegalArgumentException("Ohne Shopbuch, Shopablage und"
                    + " Freistadtbuch gibt es keinen Laden, der zahlen koennte - diese"
                    + " Klasse entsteht nur mit allen drei");
        }
        if (weltschluessel == null || weltschluessel.isBlank()) {
            throw new IllegalArgumentException("Ohne Weltschluessel findet der Lauf"
                    + " keine Stadt - und faende er keine, zahlte lautlos nie ein Laden");
        }
        this.shopbuch = shopbuch;
        this.shops = shops;
        this.buch = buch;
        this.weltschluessel = weltschluessel;
        this.uhr = uhr;
        this.wirtschaft = wirtschaft;
        this.server = server;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * Der taegliche Durchgang - gerufen von {@link Spielzeitwache} je Taktschlag.
     *
     * <p><b>Klagt nur und wirft nicht</b>, wie jeder Mitfahrer des Taktes.
     *
     * @return wie viele Shops in diesem Durchgang ABGEBAUT wurden - fuer die Probe und
     *         fuer das Protokoll. Nicht, wie viele gezahlt haben: Das ist der
     *         Normalfall, und eine Zahl darueber stuende taeglich im Protokoll.
     */
    int imTakt() {
        long betrag = wirtschaft.werte().freistaedte().ladengebuehrTag();
        if (betrag <= 0L) {
            // AB WERK der Normalfall, und darum VOR dem Tagesmerker: Wer die Gebuehr
            // nicht eingeschaltet hat, soll nicht einmal einen Tag lang gemerkt
            // bekommen, dass sie "erledigt" ist - sonst greift ein Einschalten mitten
            // am Tag erst morgen.
            return 0;
        }
        LocalDate heute = Tagesgrenze.tagVon(uhr.instant(),
                wirtschaft.werte().zeit().zone());
        if (heute.equals(zuletzt)) {
            return 0;
        }
        int abgebaut = 0;
        try {
            for (Kistenshop shop : shops.alle()) {
                if (!weltschluessel.equals(shop.schildWelt()) || shop.demBund()) {
                    // Ein BUNDSHOP gehoert niemandem - es gibt niemanden, der zahlen
                    // koennte. Dieselbe Ausnahme wie bei der Anlagegebuehr.
                    continue;
                }
                Optional<Parzelle> stadt = buch.stadtAn(shop.schildWelt(), shop.kisteX(),
                        shop.kisteZ());
                if (stadt.isEmpty()) {
                    continue;
                }
                if (!buch.erhebeLadengebuehr(stadt.get(), shop.besitzer(), betrag)) {
                    abgebaut += baueAb(shop, stadt.get(), betrag) ? 1 : 0;
                }
            }
            // ERST JETZT gemerkt: Bei einer Stoerung soll der naechste Takt es erneut
            // versuchen, und nicht erst morgen.
            zuletzt = heute;
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Die taegliche Ladengebuehr ist in einen"
                    + " Fehler gelaufen. Der naechste Takt versucht es erneut; faellt"
                    + " er laenger aus, zahlen die Laeden fuer diese Tage nicht - und"
                    + " verlieren dafuer auch keiner.", fehler);
        }
        return abgebaut;
    }

    /**
     * Baut einen Shop ab, dessen Besitzer nicht zahlen konnte.
     *
     * <p><b>Die WARE BLEIBT IN DER KISTE</b>, wie bei jedem Abbau. Der Haendler
     * verliert sein Schild und nicht seine Waren - und er kann ein neues setzen, sobald
     * er zahlen kann (dann faellt die Anlagegebuehr erneut, und das ist richtig).
     *
     * <p><b>Das Schild in der WELT bleibt stehen</b>, und das ist eine ehrliche Grenze
     * dieses Laufs: Er laeuft, ohne dass jemand in der Nahe sein muss, und einen Block
     * in einem nicht geladenen Chunk zu entfernen hiesse, ihn zu laden - fuer jeden
     * Shop einzeln. Das Schild ist danach gewoehnliches Holz: Es handelt nicht mehr,
     * denn gehandelt wird ueber die ABLAGE und nicht ueber den Text. Wer es abbaut, hat
     * ein Schild.
     */
    private boolean baueAb(Kistenshop shop, Parzelle stadt, long betrag) {
        Shopbuch.Bescheid bescheid = shopbuch.baueAb(shop.schildWelt(), shop.schildX(),
                shop.schildY(), shop.schildZ());
        if (bescheid != Shopbuch.Bescheid.ABGEBAUT) {
            protokoll.warning("Der Shop an " + shop.schluessel() + " konnte die"
                    + " Ladengebuehr von " + betrag + " Mark nicht zahlen, liess sich"
                    + " aber auch nicht abbauen (" + bescheid + "). Er handelt weiter.");
            return false;
        }
        protokoll.info("Shop an " + shop.schluessel() + " abgebaut: Die Ladengebuehr"
                + " von " + betrag + " Mark war nicht zu zahlen. Die Ware bleibt in"
                + " der Kiste.");
        // GESENDET, WENN ER DA IST - wie bei der Ladenmiete: Ein abwesender Spieler
        // bekommt nichts, und das ist hier vertretbar, weil seine Ware nicht weg ist.
        // Er findet ein gewoehnliches Schild und eine volle Kiste.
        Player besitzer = server.getPlayer(shop.besitzer());
        if (besitzer != null) {
            Shoptext.ladenGebuehrWegZeilen(sprache, stadt.name(), betrag)
                    .forEach(besitzer::sendMessage);
        }
        return true;
    }
}
