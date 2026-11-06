package de.freimarken.kern;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Die Zahlen des Verdienens - Deckel, abnehmender Ertrag und die drei Listen.
 *
 * <p>Dieselbe Bauart wie {@link Berufswerte} und {@link Zeitwerte}: ein Satz, eine
 * Vorgabe im PROGRAMM, und zu jeder Zahl eine Frage, die der Leser der
 * Einstellungsdatei stellen kann, ohne zu werfen.
 *
 * <p><b>Gerechnet wird in PFENNIGEN</b>, also in Hundertsteln einer Mark. Der Grund ist
 * eine Rechnung: Ein Spieler soll 2.000 bis 5.000 Mark je Spielstunde verdienen (Plan
 * 8.2), und in einer Stunde Arbeit baut er einige tausend Bloecke ab - das sind ein bis
 * zweieinhalb Mark je Block. Die Kasse kennt nur ganze Mark; ohne Hundertstel muesste
 * gewoehnlicher Stein 0 zahlen, und die ganze Spanne haenge an der Erzdichte statt an der
 * Arbeit. Gesammelt wird der Rest je Spieler, gebucht werden nur ganze Mark.
 *
 * <p><b>DREI Bremsen, und jede faengt etwas anderes.</b> Das ist die Lehre aus dem
 * Nachrechnen der Minderung:
 *
 * <ol>
 *   <li><b>Die Stufen</b> ({@link #vollBis}, {@link #stufe}, {@link #faktorProzent},
 *       {@link #bodenProzent}) senken den Satz je Spieler, TAETIGKEIT und Name. Sie
 *       daempfen, sie halten nichts auf: Mit den Vorgabezahlen ist die Summe der ersten
 *       800 Ereignisse eine feste Pauschale von 374 vollen Saetzen, und danach zahlt jedes
 *       weitere unbegrenzt den Boden. Eine Mobfarm nimmt beides mit.</li>
 *   <li><b>Die Stundenmenge</b> ({@link #stundenmenge}) ist die harte Obergrenze je
 *       Taetigkeit: Danach zahlt diese Taetigkeit in dieser Stunde NICHTS mehr, ueber alle
 *       Namen zusammen. Sie ist die einzige Bremse, die eine Farm wirklich anhaelt - und
 *       sie zaehlt mit Absicht ueber alle Namen, denn sonst vervielfacht eine Farm ihre
 *       Pauschale durch Artenvielfalt (fuenf Monsterarten sind fuenf Pauschalen).</li>
 *   <li><b>Der Tagesdeckel</b> ({@link #tagesdeckel}) ist die Grenze ueber alle
 *       Taetigkeiten und den ganzen Tag.</li>
 * </ol>
 *
 * <p><b>Die Stufen zaehlen je TAETIGKEIT und Name</b>, nicht je Name allein. Sonst
 * entschiede ein Namenszufall: {@code fischen cod} und {@code abbauen cod} teilten ein
 * Fenster, {@code zuechten cow} und {@code toeten cow} ebenso - und bei
 * {@code ernten wheat} gegen {@code abbauen wheat} faellt derselbe Zufall zugunsten des
 * Spielers aus.
 *
 * <p>Je NAME und nicht je Position: Eine Mobfarm liefert immer dieselbe Art an wechselnden
 * Orten, und genau sie ist der benannte Grund fuer die Regel. Je Position waere sie
 * wirkungslos.
 *
 * @param tagesdeckel   wie viele Mark ein Spieler an einem Tag hoechstens aus Arbeit
 *                      bekommt. Je SPIELER und nicht je Beruf - sonst kaeme er ueber zwei
 *                      Grundberufe doppelt (Plan 8.2)
 * @param vollBis       wie viele Ereignisse eines Namens in der gleitenden Stunde voll
 *                      zahlen
 * @param stufe         wie viele Ereignisse eine Stufe breit ist
 * @param faktorProzent auf wie viel Prozent des vorherigen Satzes jede Stufe senkt
 * @param bodenProzent  unter welchen Anteil des vollen Satzes es nie faellt
 * @param stundenmengen je Taetigkeit die harte Obergrenze je Stunde. VOLLSTAENDIG: Zu
 *                      jeder Taetigkeit steht hier eine Zahl, sonst wirft der Satz. Eine
 *                      fehlende Zeile in der Datei fuellt der Leser mit der Vorgabe auf -
 *                      eine fehlende Bremse waere die unauffaelligste Art, keine zu haben
 * @param spawngruende  aus welchen Entstehungsgruenden ein Wesen beim Toeten zahlt, in
 *                      Kleinbuchstaben. Eine WEISSLISTE und keine Schwarzliste: Ein neuer
 *                      Grund in einer kuenftigen Spielfassung zahlt dann nichts, statt
 *                      versehentlich zu zahlen
 * @param saatgut       welche gesetzten Bloecke KEINE Marke bekommen, in Kleinbuchstaben.
 *                      Die eine Ausnahme von "ein Block zahlt nur einmal", und sie steht
 *                      in der Datei und nicht im Programm
 * @param loehne        was jede Taetigkeit an jedem Namen einbringt. Die TAFEL gehoert
 *                      in denselben Satz wie die Zahlen, nicht daneben: Ein Neuladen
 *                      tauscht beides als EIN Stueck, und wer mitten in der Rechnung
 *                      einen neuen Lohn aus einer alten Minderung bekaeme, verdiente
 *                      einen Augenblick lang etwas, das niemand eingestellt hat
 */
public record Verdienstwerte(long tagesdeckel, int vollBis, int stufe, int faktorProzent,
                             int bodenProzent, Map<Taetigkeit, Integer> stundenmengen,
                             Set<String> spawngruende, Set<String> saatgut,
                             Lohntafel loehne) {

    public static final String PFAD_TAGESDECKEL = "verdienst.tagesdeckel";

    public static final String PFAD_VOLL_BIS = "verdienst.minderung.voll-bis";

    public static final String PFAD_STUFE = "verdienst.minderung.stufe";

    public static final String PFAD_FAKTOR = "verdienst.minderung.faktor-prozent";

    public static final String PFAD_BODEN = "verdienst.minderung.boden-prozent";

    /** Der Abschnitt mit einer Zahl je Taetigkeit - die harte Obergrenze je Stunde. */
    public static final String PFAD_STUNDENMENGE = "verdienst.minderung.stundenmenge";

    /** Die Weissliste der Entstehungsgruende, aus denen ein Wesen beim Toeten zahlt. */
    public static final String PFAD_SPAWNGRUENDE = "verdienst.spawngruende";

    /** Die Bloecke, die beim Setzen KEINE Marke bekommen - Saatgut waechst nach. */
    public static final String PFAD_SAATGUT = "verdienst.saatgut";

    /** Der Abschnitt mit den Loehnen - je Beruf, Taetigkeit und Name. */
    public static final String PFAD_LOEHNE = "verdienst.loehne";

    /**
     * 20.000 Mark am Tag.
     *
     * <p>Gerechnet gegen die Kalibrierung: Bei 2.000 bis 5.000 Mark je Spielstunde sind
     * das vier bis zehn Stunden Arbeit AN EINEM TAG. Wer das erreicht, farmt - und der
     * Deckel soll "grosszuegig genug sein, dass ihn normales Spielen nicht erreicht"
     * (Plan 9.1). Zwei Stunden waeren zu knapp fuer einen Samstag, hunderttausend waeren
     * kein Deckel.
     */
    public static final long VORGABE_TAGESDECKEL = 20_000L;

    /**
     * Die ersten 200 Ereignisse eines Namens zahlen voll.
     *
     * <p>Gerechnet gegen das normale Spielen: Ein Haus sind einige hundert Bloecke, aber
     * selten zweihundert derselben ART in einer Stunde. Ein Stollen in den Stein geht
     * darueber hinaus - und genau dort soll die Minderung greifen.
     */
    public static final int VORGABE_VOLL_BIS = 200;

    public static final int VORGABE_STUFE = 200;

    public static final int VORGABE_FAKTOR_PROZENT = 50;

    /**
     * Zehn Prozent, und nicht null.
     *
     * <p>Ein Boden von 0 waere eine unsichtbare Wand: Ab einer Zahl, die niemand kennt,
     * passiert gar nichts mehr, und der Spieler sucht den Fehler bei sich. Ein Zehntel
     * ist spuerbar wenig und bleibt eine Antwort.
     */
    public static final int VORGABE_BODEN_PROZENT = 10;

    /** Der grosszuegigste Deckel, der sich noch rechnen laesst. */
    public static final long HOECHSTER_TAGESDECKEL = Long.MAX_VALUE / 4L;

    /** Wie viele Ereignisse eine Stufe hoechstens breit sein darf. */
    public static final int HOECHSTE_STUFE = 1_000_000;

    /**
     * Je Taetigkeit die Vorgabe fuer die harte Stundenmenge.
     *
     * <p>Sechs Zahlen im Programm und keine zweite Abschrift einer Tabelle: Anders als die
     * Lohnliste sind das so wenige Werte, dass sie nicht fehlen DUERFEN - eine fehlende
     * Bremse waere die unauffaelligste Art, keine zu haben. Jede Zahl ist gegen die
     * menschliche Rate gerechnet, nicht gegen die der Maschine:
     *
     * <ul>
     *   <li><b>abbauen und setzen 4.000</b> - ein Stollen oder eine Hauswand in einer
     *       Stunde. Hier ist die Zahl absichtlich hoch: Diese zwei sind kalibriert, und
     *       die Stufen tun dort ihre Arbeit.</li>
     *   <li><b>ernten 2.000</b> - ein grosses Feld. Faengt die Kuerbisstiele, die ohne
     *       Spieler nachwachsen und an einer nie markierten Position stehen.</li>
     *   <li><b>fischen 300</b> - mit Koeder III liegt der Biss bei etwa fuenf Sekunden,
     *       also bei 650 Faengen je Stunde als OBERGRENZE fuer Mensch UND Klickautomat.
     *       300 laesst den Fischer verdienen und laesst den Automaten nach einer halben
     *       Stunde leerlaufen.</li>
     *   <li><b>toeten 300</b> - im Hoehlengang faellt ein Mob alle halbe Minute, also 60
     *       bis 150 je Stunde. Eine natuerliche Mobfarm liegt um Zehnerpotenzen
     *       darueber.</li>
     *   <li><b>zuechten 150</b> - die Abklingzeit je Elterntier sind fuenf Minuten, also
     *       zwoelf Zuchten je Stunde und Paar. 150 sind rund ein Dutzend Paare.</li>
     * </ul>
     */
    public static final Map<Taetigkeit, Integer> VORGABE_STUNDENMENGEN = Map.of(
            Taetigkeit.ABBAUEN, 4_000,
            Taetigkeit.SETZEN, 4_000,
            Taetigkeit.ERNTEN, 2_000,
            Taetigkeit.FISCHEN, 300,
            Taetigkeit.TOETEN, 300,
            Taetigkeit.ZUECHTEN, 150);

    /**
     * Die Entstehungsgruende, aus denen ein getoetetes Wesen zahlt.
     *
     * <p>Das ist die Blockmarke fuer Wesen, und sie loest zwei Maschen auf einmal: Ein
     * GEZUECHTETES Kalb zahlt nicht noch einmal beim Tod (Grund {@code breeding}), und
     * eine Spawnerfarm zahlt gar nicht ({@code spawner}, {@code trial_spawner},
     * {@code spawner_egg}).
     *
     * <p>Eine WEISSLISTE: Was hier nicht steht, zahlt nicht. Minecraft bringt mit jeder
     * Fassung neue Gruende mit, und ein neuer Grund soll kein neues Geld bedeuten.
     */
    public static final Set<String> VORGABE_SPAWNGRUENDE = Set.of(
            "natural", "chunk_gen", "jockey", "raid", "patrol", "village_invasion",
            "trap");

    /**
     * Die gesetzten Bloecke, die KEINE Marke bekommen.
     *
     * <p>Die eine Ausnahme von "ein Block zahlt nur einmal", und sie ist noetig, weil
     * Saatgut auf DERSELBEN Position liegt wie die reife Pflanze: Ein von Hand gesaetes
     * Feld wuerde mit Marke nie zahlen.
     *
     * <p>Es sind die BLOCKNAMEN und nicht die Gegenstaende in der Hand - die Setzwache
     * sieht den gesetzten Block. Gesaeter Weizen ist {@code wheat} und nicht
     * {@code wheat_seeds}, eine Karotte {@code carrots}, eine Melonensaat
     * {@code melon_stem}. Jeder Name hier ist gegen das Jar geprueft.
     *
     * <p><b>Setzlinge stehen NICHT darin</b>, und das ist kein Vergessen: Ein Setzling
     * wird kein geerntetes Feld, sondern ein Baum, und dessen Stamm steht an neuen,
     * unmarkierten Positionen. Nur der unterste Block wuerde nichts bringen - weniger
     * Ausnahme ist besser als eine, die man erklaeren muss.
     */
    public static final Set<String> VORGABE_SAATGUT = Set.of(
            "wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem",
            "sugar_cane", "bamboo_sapling", "bamboo", "cactus", "kelp", "cocoa",
            "nether_wart", "sweet_berry_bush", "torchflower_crop", "pitcher_crop");

    /**
     * Die Vorgabe traegt eine LEERE Lohntafel - und das ist Absicht.
     *
     * <p>Jede andere Vorgabe dieses Projekts steht im Programm, damit sie nicht fehlen
     * kann. Eine Lohntafel mit hundert Zeilen dort waere aber eine zweite Abschrift der
     * ausgelieferten Datei, und zwei Abschriften derselben Tabelle laufen auseinander -
     * genau wie beim Abschnitt {@code welten:}, der ebenfalls keinen Rueckfall im
     * Programm hat.
     *
     * <p>Fehlt die Liste, verdient also niemand etwas. Das ist die sichere Richtung: Ein
     * geratener Lohn macht Geld, das keine Ruecksicherung zurueckholt. Der Leser der
     * Einstellungsdatei sagt es laut, und der Start nennt die Zahl der gelesenen Zeilen.
     */
    public static final Verdienstwerte VORGABE = new Verdienstwerte(VORGABE_TAGESDECKEL,
            VORGABE_VOLL_BIS, VORGABE_STUFE, VORGABE_FAKTOR_PROZENT,
            VORGABE_BODEN_PROZENT, VORGABE_STUNDENMENGEN, VORGABE_SPAWNGRUENDE,
            VORGABE_SAATGUT, Lohntafel.ausLoehnen(List.of()));

    public Verdienstwerte {
        if (loehne == null) {
            throw new IllegalArgumentException("Ohne Lohntafel gibt es keinen Verdienst;"
                    + " eine leere Tafel ist erlaubt und heisst: nichts bringt etwas ein");
        }
        if (!brauchbarerDeckel(tagesdeckel)) {
            throw new IllegalArgumentException("Tagesdeckel " + tagesdeckel + " liegt"
                    + " nicht zwischen 1 und " + HOECHSTER_TAGESDECKEL
                    + ". Eine 0 hiesse, niemand verdient etwas - wer die Berufe"
                    + " abschalten will, laesst die Lohnliste leer.");
        }
        if (!brauchbareAnzahl(vollBis)) {
            throw new IllegalArgumentException("voll-bis ist " + vollBis + " - erlaubt"
                    + " ist 1 bis " + HOECHSTE_STUFE + ". Eine 0 hiesse, dass schon der"
                    + " erste Block gemindert ist.");
        }
        if (!brauchbareAnzahl(stufe)) {
            throw new IllegalArgumentException("stufe ist " + stufe + " - erlaubt ist 1"
                    + " bis " + HOECHSTE_STUFE + ". Eine 0 teilte die Rechnung durch"
                    + " null.");
        }
        if (!brauchbarerFaktor(faktorProzent)) {
            throw new IllegalArgumentException("faktor-prozent ist " + faktorProzent
                    + " - erlaubt ist 1 bis 99. Bei 100 mindert nichts, bei 0 faellt der"
                    + " Satz in einem Schritt auf den Boden; beides ist keine Stufe.");
        }
        if (!brauchbarerBoden(bodenProzent)) {
            throw new IllegalArgumentException("boden-prozent ist " + bodenProzent
                    + " - erlaubt ist 1 bis 100. Eine 0 waere eine unsichtbare Wand: Ab"
                    + " einer Zahl, die niemand kennt, passiert gar nichts mehr.");
        }
        stundenmengen = gepruefteMengen(stundenmengen);
        spawngruende = kleineMenge(spawngruende, "spawngruende");
        saatgut = kleineMenge(saatgut, "saatgut");
    }

    /**
     * Jede Taetigkeit braucht ihre Stundenmenge - eine fehlende waere keine Bremse.
     *
     * <p>Der Wurf und nicht ein stilles Auffuellen: Der LESER fuellt eine fehlende Zeile
     * mit der Vorgabe auf und sagt es im Bericht. Wer diesen Satz von Hand baut und eine
     * Taetigkeit vergisst, hat einen Zweig ohne Obergrenze gebaut, und das soll laut
     * sein.
     */
    private static Map<Taetigkeit, Integer> gepruefteMengen(
            Map<Taetigkeit, Integer> gegeben) {
        if (gegeben == null) {
            throw new IllegalArgumentException("Ohne Stundenmengen gibt es keine harte"
                    + " Obergrenze - und die ist die einzige Bremse, die eine Farm"
                    + " anhaelt");
        }
        Map<Taetigkeit, Integer> geordnet = new LinkedHashMap<>();
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            Integer menge = gegeben.get(taetigkeit);
            if (menge == null) {
                throw new IllegalArgumentException("Fuer " + taetigkeit.schluessel()
                        + " fehlt die Stundenmenge. Jede Taetigkeit braucht eine"
                        + " Obergrenze, sonst ist sie die eine ohne Bremse.");
            }
            if (!brauchbareAnzahl(menge)) {
                throw new IllegalArgumentException(PFAD_STUNDENMENGE + "."
                        + taetigkeit.schluessel() + " ist " + menge + " - erlaubt ist 1"
                        + " bis " + HOECHSTE_STUFE + ". Eine 0 hiesse, dass diese"
                        + " Taetigkeit nie etwas einbringt; wer das will, laesst ihre"
                        + " Lohnzeilen weg.");
            }
            geordnet.put(taetigkeit, menge);
        }
        return Map.copyOf(geordnet);
    }

    /** Eine Namensmenge, kleingeschrieben und ohne Leeres - damit ein Vergleich traegt. */
    private static Set<String> kleineMenge(Set<String> gegeben, String was) {
        if (gegeben == null) {
            throw new IllegalArgumentException("Ohne " + was + " gibt es keine Liste;"
                    + " eine leere Menge ist erlaubt");
        }
        Set<String> sauber = new LinkedHashSet<>();
        for (String name : gegeben) {
            if (name != null && !name.isBlank()) {
                sauber.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(sauber);
    }

    /**
     * Die harte Obergrenze dieser Taetigkeit je Stunde.
     *
     * <p>Ein reiner Nachschlag: Der Satz ist vollstaendig, dafuer sorgt sein
     * Konstruktor.
     */
    public int stundenmenge(Taetigkeit taetigkeit) {
        if (taetigkeit == null) {
            throw new IllegalArgumentException("Ohne Taetigkeit gibt es keine"
                    + " Obergrenze");
        }
        return stundenmengen.get(taetigkeit);
    }

    /** Ob dieser gesetzte Block ohne Marke bleibt - er waechst nach. */
    public boolean istSaatgut(String material) {
        return material != null
                && saatgut.contains(material.trim().toLowerCase(Locale.ROOT));
    }

    /** Ob ein Wesen aus diesem Grund entstanden beim Toeten zahlt. */
    public boolean zahltAusGrund(String spawngrund) {
        return spawngrund != null
                && spawngruende.contains(spawngrund.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Dieselben Werte mit anderem Deckel und anderer Minderung.
     *
     * <p><b>Warum es diese Abwandler gibt:</b> Dieser Satz hat neun Teile, und mit jedem
     * Bauschritt kommt einer dazu. Jede Probe, die ihn mit allen neun baut, bricht dann -
     * und zwar nicht, weil ihre Frage falsch waere, sondern weil ein Teil dazugekommen
     * ist, der sie nichts angeht. Mit einem Abwandler nennt eine Probe genau das, was sie
     * veraendert, und schweigt ueber den Rest.
     */
    public Verdienstwerte mitMinderung(long deckel, int bisVoll, int breite,
                                       int faktor, int boden) {
        return new Verdienstwerte(deckel, bisVoll, breite, faktor, boden, stundenmengen,
                spawngruende, saatgut, loehne);
    }

    /** Dieselben Werte mit einer anderen Lohntafel. */
    public Verdienstwerte mitLoehnen(Lohntafel andere) {
        return new Verdienstwerte(tagesdeckel, vollBis, stufe, faktorProzent,
                bodenProzent, stundenmengen, spawngruende, saatgut, andere);
    }

    /** Dieselben Werte mit einer anderen Stundenmenge fuer EINE Taetigkeit. */
    public Verdienstwerte mitStundenmenge(Taetigkeit taetigkeit, int menge) {
        Map<Taetigkeit, Integer> geaendert = new LinkedHashMap<>(stundenmengen);
        geaendert.put(taetigkeit, menge);
        return new Verdienstwerte(tagesdeckel, vollBis, stufe, faktorProzent,
                bodenProzent, geaendert, spawngruende, saatgut, loehne);
    }

    /** Dieselben Werte mit einer anderen Weissliste der Entstehungsgruende. */
    public Verdienstwerte mitSpawngruenden(Set<String> andere) {
        return new Verdienstwerte(tagesdeckel, vollBis, stufe, faktorProzent,
                bodenProzent, stundenmengen, andere, saatgut, loehne);
    }

    /** Dieselben Werte mit einer anderen Saatgutliste. */
    public Verdienstwerte mitSaatgut(Set<String> andere) {
        return new Verdienstwerte(tagesdeckel, vollBis, stufe, faktorProzent,
                bodenProzent, stundenmengen, spawngruende, andere, loehne);
    }

    public static boolean brauchbarerDeckel(long mark) {
        return mark >= 1L && mark <= HOECHSTER_TAGESDECKEL;
    }

    public static boolean brauchbareAnzahl(long zahl) {
        return zahl >= 1L && zahl <= HOECHSTE_STUFE;
    }

    /**
     * Ein Faktor von 1 bis 99 Prozent.
     *
     * <p>Nicht 100: Dann mindert nichts, und die Regel waere stumm aus - wer sie
     * abschalten will, setzt {@code voll-bis} hoch. Nicht 0: Dann faellt der Satz in
     * einem Schritt auf den Boden, und die Stufen daneben waeren eine Zierde.
     */
    public static boolean brauchbarerFaktor(long prozent) {
        return prozent >= 1L && prozent <= 99L;
    }

    public static boolean brauchbarerBoden(long prozent) {
        return prozent >= 1L && prozent <= 100L;
    }
}
