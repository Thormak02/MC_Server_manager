package de.freimarken.paper;

import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blockzeile;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Rueckrollplan;
import de.freimarken.kern.Rueckweggrenzen;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Nimmt zurueck, was in einem Kasten in einem Zeitfenster geschah.
 *
 * <p>Die RECHNUNG steht in {@link Rueckrollplan} und ohne eine Zeile Bukkit; hier steht
 * das, was dafuer einen laufenden Server braucht - die Welt finden, Bloecke setzen,
 * Nummern merken. Die Teilung ist nicht Geschmack: {@code createBlockData} und
 * {@code setBlockData} werfen ohne Server, also waere eine Rechnung an dieser Stelle in
 * keiner Probe erreichbar.
 *
 * <p><b>Nicht {@code Rueckweg} - der Name war vergeben.</b> Jene Klasse merkt, wo ein
 * Spieler zuletzt war, fuer {@code /zurueck}. Zwei Dinge mit demselben Namen in einem
 * Paket waeren der Fehler, bei dem jemand die falsche aendert.
 *
 * <p><b>Zweimal gelesen, und das mit Absicht.</b> Die Vorschau liest, und die Ausfuehrung
 * liest erneut - sie arbeitet NICHT mit dem Plan der Vorschau. Zwischen beiden liegt die
 * Bestaetigung des Betreibers, und in dieser Zeit kann weitergebaut worden sein. Ein Plan
 * aus der Vorschau stellte dann einen Zustand her, den der Betreiber nicht mehr gesehen
 * hat. Was er bestaetigt, ist der KASTEN und das Fenster, nicht eine Liste von Bloecken.
 *
 * <p><b>Die Rueckrollung schreibt NICHT ins Blockbuch</b>, und das ist eine der
 * wichtigsten Entscheidungen hier. Die Versuchung ist gross - "wer hat zurueckgerollt"
 * ist eine gute Frage -, aber die Antwort waere eine Falle: Die gesetzten Bloecke stuenden
 * als neue Zeilen im Buch, und eine zweite Rueckrollung ueber denselben Kasten naehme
 * GENAU SIE zurueck. Der Betreiber, der zweimal hintereinander denselben Befehl tippt,
 * haette den Schaden wiederhergestellt. Aufgeschrieben wird darum im Serverprotokoll, mit
 * Namen, Kasten und Zahlen - dort, wo es niemand wieder zurueckrollen kann.
 *
 * <p><b>Gemerkt werden auch die Zeilen, deren Block nicht gesetzt werden konnte.</b> Sonst
 * versuchte jeder weitere Lauf dasselbe wieder, mit demselben Ergebnis - ein einziger
 * unbrauchbarer Zustandstext blockierte den Kasten fuer immer. Wie viele es waren, sagt
 * der Befehl; der Betreiber soll wissen, dass dort noch etwas steht.
 */
final class Rueckrollung {

    /**
     * Was eine Rueckrollung vom Server braucht - die ganze Naht, drei Methoden.
     *
     * <p>Ein Fingerbreit Naht und kein Rahmenwerk, aus demselben Grund wie
     * {@link BlockBefehle.Blickfang}: Ohne sie waere dieser ganze Zweig ungepruefbar,
     * denn jede der drei Antworten verlangt einen laufenden Server.
     */
    interface Hand {

        /**
         * Die Welt zu diesem getippten Namen - oder {@code null}, wenn es sie nicht gibt.
         *
         * @param getippt was der Betreiber geschrieben hat, mit oder ohne Namensraum
         */
        Weltlage welt(String getippt);

        /**
         * Setzt an dieser Stelle diesen Zustand.
         *
         * <p>Ohne Physik und ohne Nachbarbenachrichtigung: Bei tausend Bloecken stuende
         * der Server sonst, und ein fallender Sandblock landete an einer Stelle, an der
         * er nie stand.
         *
         * @return {@code false}, wenn der Zustandstext kein Block ist, den dieser Server
         *         kennt. Das ist kein Fehler des Betreibers: Eine Zeile aus einer
         *         frueheren Minecraft-Fassung kann einen Block nennen, den es nicht mehr
         *         gibt
         */
        boolean setze(Blockort ort, String zustand);

        /** Die Welten, die es gibt - fuer die Vorschlaege des Befehls. */
        List<String> weltnamen();
    }

    /**
     * Eine Welt, soweit die Rueckrollung sie kennt.
     *
     * @param schluessel wie {@code World.key().asString()} ihn liefert
     * @param unten      die Weltunterkante
     * @param oben       die erste Hoehe, die es NICHT mehr gibt
     */
    record Weltlage(String schluessel, int unten, int oben) {
    }

    /**
     * Was eine Rueckrollung taete - gerechnet, nicht getan.
     *
     * @param kasten         der Bereich, auf den sich alles bezieht
     * @param plan           was zu setzen waere; bei {@link #zuViele} immer leer
     * @param gelesen        wie viele Zeilen die Abfrage gab
     * @param ohneVorzustand wie viele davon ohne Vorzustand auskommen muessen
     * @param zuViele        ob die Grenze ueberschritten ist - dann wird NICHT gerollt
     */
    record Schau(Quader kasten, Rueckrollplan.Plan plan, int gelesen,
                 int ohneVorzustand, boolean zuViele) {

        /** Ob es nichts zu tun gibt - der haeufigste Fall, und kein Fehler. */
        boolean leer() {
            return !zuViele && plan.leer();
        }

        /** Wie viele Stellen weggenommen werden. */
        int wegnehmen() {
            int wie = 0;
            for (Rueckrollplan.Setzung setzung : plan.setzungen()) {
                if (setzung.wegnehmen()) {
                    wie++;
                }
            }
            return wie;
        }

        /** Wie viele Stellen wiederhergestellt werden. */
        int herstellen() {
            return plan.setzungen().size() - wegnehmen();
        }
    }

    /**
     * Was ein Lauf getan hat.
     *
     * @param gesetzt    wie viele Bloecke wirklich gesetzt wurden
     * @param misslungen wie viele Zustandstexte dieser Server nicht kennt
     * @param gemerkt    wie viele Zeilen als zurueckgenommen vermerkt sind
     */
    record Lauf(int gesetzt, int misslungen, int gemerkt) {
    }

    private final Blockbuchablage ablage;
    private final Rueckweggrenzen grenzen;
    private final Hand hand;
    private final Logger protokoll;

    Rueckrollung(Blockbuchablage ablage, Rueckweggrenzen grenzen, Hand hand,
                 Logger protokoll) {
        if (ablage == null || grenzen == null || hand == null || protokoll == null) {
            throw new IllegalArgumentException("Ablage, Grenzen, Hand und Protokoll sind"
                    + " Pflicht");
        }
        this.ablage = ablage;
        this.grenzen = grenzen;
        this.hand = hand;
        this.protokoll = protokoll;
    }

    /** Die Grenzen, die gelten - fuer die Absagen des Befehls. */
    Rueckweggrenzen grenzen() {
        return grenzen;
    }

    /** Die Welt zu einem getippten Namen - oder {@code null}. */
    Weltlage welt(String getippt) {
        return hand.welt(getippt);
    }

    /** Die Welten, die es gibt. */
    List<String> weltnamen() {
        return hand.weltnamen();
    }

    /**
     * Rechnet, was eine Rueckrollung taete.
     *
     * <p><b>Gelesen wird mit {@code Grenze + 1}</b>, und dieser eine Zaehler ist der
     * ganze Riegel: Mit genau der Grenze als Limit kaeme bei einem vollen Kasten eine
     * Liste zurueck, die aussieht wie ein vollstaendiges Ergebnis - und der Lauf rollte
     * die aeltesten Zeilen zurueck und merkte sie, waehrend die juengeren desselben
     * Blocks stehen blieben. Der naechste Lauf stellte dann deren Vorzustand her, also
     * den ZWISCHENSTAND. Mit der einen Zeile darueber ist "zu viele" vom "genau voll"
     * unterscheidbar, und der Befehl kann absagen.
     *
     * @param von der aeltere Rand des Fensters, eingeschlossen
     * @param bis der juengere Rand, eingeschlossen
     */
    Schau schau(Quader kasten, String weltschluessel, Instant von, Instant bis) {
        List<Blockzeile> zeilen = ablage.imBereich(weltschluessel, kasten, von, bis,
                grenzen.hoechsteBloecke() + 1);
        if (zeilen.size() > grenzen.hoechsteBloecke()) {
            return new Schau(kasten, new Rueckrollplan.Plan(List.of(), List.of(), 0),
                    zeilen.size(), 0, true);
        }
        return new Schau(kasten, Rueckrollplan.aus(zeilen), zeilen.size(),
                Rueckrollplan.ohneVorzustand(zeilen), false);
    }

    /**
     * Fuehrt aus, was die Schau rechnet - und merkt die Zeilen.
     *
     * <p>Die Reihenfolge des Plans wird NICHT angetastet: erst wegnehmen von oben, dann
     * herstellen von unten. Sie ist der Teil, den {@link Rueckrollplan} begruendet.
     *
     * <p><b>Gemerkt wird NACH dem Setzen</b>, und auch das ist eine Entscheidung: Wer
     * zuerst merkt und dann setzt, hat bei einem Abbruch mitten im Lauf Zeilen, die als
     * zurueckgenommen gelten, obwohl ihr Block noch steht - und kein zweiter Lauf fasst
     * sie wieder an. In der anderen Richtung bleibt bei einem Abbruch der Griff im Buch
     * stehen und laesst sich wiederholen; was zweimal auf denselben Zustand gesetzt
     * wird, bleibt derselbe Zustand.
     *
     * @param wer wessen Name ins Serverprotokoll kommt
     */
    Lauf fuehre(Schau schau, String weltschluessel, String wer) {
        if (schau == null || schau.zuViele()) {
            throw new IllegalArgumentException("Ein Lauf ohne Schau oder ueber der"
                    + " Grenze - der Befehl muss vorher absagen");
        }
        int gesetzt = 0;
        int misslungen = 0;
        for (Rueckrollplan.Setzung setzung : schau.plan().setzungen()) {
            if (hand.setze(setzung.ort(), setzung.zustand())) {
                gesetzt++;
            } else {
                misslungen++;
            }
        }
        int gemerkt = gemerkt(schau.plan().nummern());
        // INS SERVERPROTOKOLL und nicht ins Blockbuch - siehe den Kopf der Klasse. Mit
        // dem Namen, dem Kasten und den Zahlen: Eine Rueckrollung ist der groesste
        // Eingriff, den dieses Plugin in eine fertige Welt macht, und wer ihn gemacht
        // hat, muss hinterher feststellbar sein.
        protokoll.info("Rueckrollung von " + wer + " in " + weltschluessel + " "
                + schau.kasten().alsText() + ": " + gesetzt + " Bloecke gesetzt, "
                + misslungen + " misslungen, " + gemerkt + " von "
                + schau.plan().nummern().size() + " Zeilen gemerkt.");
        return new Lauf(gesetzt, misslungen, gemerkt);
    }

    /**
     * Merkt die Zeilen - und laesst eine Stoerung dabei den Lauf nicht verschlucken.
     *
     * <p>Die Bloecke stehen zu diesem Zeitpunkt schon. Faellt die Datenbank genau jetzt
     * aus, ist die Rueckrollung getan und nur nicht vermerkt; ein zweiter Lauf wuerde
     * dieselben Zustaende noch einmal setzen, und das ist harmlos. Ein Wurf hier liesse
     * den Betreiber dagegen glauben, nichts sei geschehen - und das waere die falsche
     * Auskunft.
     */
    private int gemerkt(List<Long> nummern) {
        try {
            return ablage.merkeZurueckgenommen(nummern);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Die Bloecke sind gesetzt, aber die Zeilen"
                    + " liessen sich nicht als zurueckgenommen merken - eine zweite"
                    + " Rueckrollung ueber denselben Kasten wuerde sie wieder sehen und"
                    + " dieselben Zustaende noch einmal setzen. Das ist harmlos, sieht"
                    + " aber nach doppelter Arbeit aus.", fehler);
            return 0;
        }
    }

    /**
     * Die Fassung fuers Spiel: Welt finden, Block setzen, Welten nennen.
     *
     * <p>An EINER Stelle gebaut und nicht im Plugin verstreut - dieselbe Bauart und
     * derselbe Grund wie bei {@link BlockBefehle#ueberBukkit}: Die drei Nahtstuecke
     * beantworten dieselbe Frage ("was ist da draussen") und gehoeren beisammen.
     *
     * <p><b>Die Welt wird GESUCHT und nicht nachgeschlagen</b>, und das ist eine
     * Entscheidung mit Grund: {@code NamespacedKey.fromString} wirft bei einem
     * getippten Namen mit einem Zeichen, das kein Schluesselzeichen ist - ein
     * Grossbuchstabe genuegt -, und dieser Name kommt aus einer Befehlszeile. Eine
     * Schleife ueber die Welten dieses Servers kostet bei einem halben Dutzend
     * nichts und nimmt beide Formen: {@code altfurt} und {@code freimarken:altfurt}.
     *
     * <p><b>Der kurze Name kommt aus dem SCHLUESSEL und nicht aus
     * {@code getName()}</b>, und auch das ist gemessen: Die Welten dieses Servers
     * entstehen mit {@code WorldCreator.ofKey}, und deren Name traegt dann den
     * Namensraum als Ordnerpfad. Der kurze Teil des Schluessels ist dagegen genau
     * das Wort, das der Betreiber in seiner {@code config.yml} geschrieben hat - und
     * genau das, was {@link Blocktext#bestaetigung} in die Befehlszeile setzt. Zwei
     * Stellen mit verschiedenen Namen fuer dieselbe Welt gaeben eine Vorschau, deren
     * Bestaetigungszeile nicht laeuft.
     */
    static Rueckrollung ueberBukkit(Blockbuchablage ablage, Rueckweggrenzen grenzen,
                                    Server server, Logger protokoll) {
        return new Rueckrollung(ablage, grenzen, new Hand() {

            @Override
            public Weltlage welt(String getippt) {
                World welt = gesucht(getippt);
                return welt == null ? null
                        : new Weltlage(welt.key().asString(), welt.getMinHeight(),
                                welt.getMaxHeight());
            }

            @Override
            public boolean setze(Blockort ort, String zustand) {
                World welt = gesucht(ort.welt());
                if (welt == null) {
                    // Erreichbar: Zwischen Vorschau und Lauf kann eine Welt
                    // entladen worden sein. Kein Wurf - der Lauf soll die uebrigen
                    // Bloecke setzen und die Zahl am Ende nennen.
                    return false;
                }
                try {
                    BlockData daten = server.createBlockData(zustand);
                    // OHNE PHYSIK (false): Bei tausend Bloecken stuende der Server
                    // sonst, und ein fallender Sandblock landete an einer Stelle, an
                    // der er nie stand - der Rueckweg hat den Schaden dann
                    // vergroessert.
                    welt.getBlockAt(ort.x(), ort.y(), ort.z())
                            .setBlockData(daten, false);
                    return true;
                } catch (IllegalArgumentException fehler) {
                    // KEIN Protokolleintrag je Block: Bei einer alten Zeilenreihe
                    // waeren das tausend gleiche Zeilen. Die ZAHL nennt der Befehl,
                    // und sie ist die Angabe, auf die es ankommt.
                    return false;
                }
            }

            @Override
            public List<String> weltnamen() {
                List<String> namen = new java.util.ArrayList<>();
                for (World welt : server.getWorlds()) {
                    namen.add(Blocktext.kurzeWelt(welt.key().asString()));
                }
                return List.copyOf(namen);
            }

            /**
             * Die Welt zu einem Namen in beiden Formen - oder {@code null}.
             *
             * <p>Die ENTSCHEIDUNG trifft {@link Blocktext#istDieseWelt}, und sie
             * steht dort, weil sie hier unpruefbar waere: Eine Einspritzung in
             * diese Naht lief am 26.11.2026 gegen keine einzige Probe (Fall V).
             * Hier bleibt nur die Schleife ueber die Welten dieses Servers.
             */
            private World gesucht(String getippt) {
                for (World welt : server.getWorlds()) {
                    if (Blocktext.istDieseWelt(welt.key().asString(),
                            getippt)) {
                        return welt;
                    }
                }
                return null;
            }
        }, protokoll);
    }
}
