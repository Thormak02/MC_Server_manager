package de.freimarken.kern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Was das Blockbuch aufschreibt, wie lange es das behaelt - und das JE WELT.
 *
 * <p><b>Keine Wahrheitswerte in der Einstellungsdatei, sondern Weisslisten von Namen.</b>
 * Das ist die wichtigste Entscheidung dieser Klasse, und sie ist gemessen: Das Projekt
 * kennt kein einziges {@code getBoolean} in {@code paper/} oder {@code core/}, und zwar
 * mit Grund. Bukkit liest jedes Wort ausser {@code true} stumm als {@code false} - aus
 * einem {@code benutzen: ja} wuerde ein abgeschaltetes Blockbuch ohne eine Zeile Klage,
 * und der Betreiber suchte Monate spaeter die Taten, die er aufzuschreiben glaubte. Eine
 * Weissliste beklagt dagegen jeden Namen, den sie nicht kennt, und nennt die erlaubten.
 * Dasselbe Muster wie {@code verdienst.spawngruende} und {@code messstelle.arten}.
 *
 * <p><b>Je Welt eine eigene Auswahl, mit Rueckfall auf die Vorgabe.</b> Die drei Welten
 * dieses Servers stellen verschiedene Fragen: In der Marktstadt ist "wer war in meiner
 * Truhe" der ganze Zweck, im Nether interessiert niemanden, wer eine Tuer geoeffnet hat.
 * Eine einzige Liste fuer alle Welten hiesse, die teuerste Tat ueberall zu schreiben oder
 * nirgends.
 *
 * <p><b>Eine LEERE Auswahl ist erlaubt und heisst etwas</b>: In dieser Welt wird nichts
 * aufgeschrieben. Das ist kein Rueckfall auf die Vorgabe - wer eine leere Liste eintraegt,
 * meint nicht die Vorgabe, sondern "hier nicht". Dieselbe Entscheidung wie bei der
 * Saatgutliste.
 *
 * <p><b>Die Frist steht in TAGEN und wird in Java gerechnet</b>, nie in SQL: Ein
 * {@code WHERE zeitpunkt < NOW() - INTERVAL 30 DAY} fragte die Uhr und die Zone der
 * Datenbank, und die eingestellte Zone waere stumm wirkungslos. Die Grenze rechnet
 * {@link Tagesgrenze}, und sie kommt als Zeitpunkt in die Abfrage.
 */
public final class Blockbuchwerte {

    /** Wie lange eine Zeile bleibt, in Tagen. */
    public static final String PFAD_FRIST = "blockbuch.aufbewahrung-tage";

    /** Die Vorgabe: welche Taten eine Welt aufschreibt, die nichts eigenes sagt. */
    public static final String PFAD_TATEN = "blockbuch.taten";

    /** Darunter je Welt eine eigene Liste. */
    public static final String PFAD_WELTEN = "blockbuch.welten";

    /** Wie viele Zeilen ein Aufraeumschlag hoechstens loescht. */
    public static final String PFAD_HAPPEN = "blockbuch.je-aufraeumen";

    /** Welche Bloecke die Tat {@code benutzen} ueberhaupt aufschreibt. */
    public static final String PFAD_BENUTZBAR = "blockbuch.benutzbare-bloecke";

    /** Wie weit der Blick des Befehls reicht, in Bloecken. */
    public static final String PFAD_REICHWEITE = "blockbuch.reichweite";

    /** Wie viele Zeilen eine Abfrage hoechstens zeigt. */
    public static final String PFAD_ZEILEN = "blockbuch.zeilen-je-abfrage";

    /**
     * Dreissig Tage.
     *
     * <p>Lang genug fuer die Moderation - eine Beschwerde kommt selten am selben Tag -
     * und kurz genug, dass die Tabelle nicht unbegrenzt waechst. Wer laenger
     * aufbewahren will, traegt es ein; die Obergrenze ist
     * {@link #HOECHSTE_FRIST_TAGE}.
     */
    public static final int VORGABE_FRIST_TAGE = 30;

    /**
     * Zehn Jahre als Obergrenze - und sie ist eine Bremse gegen den Vertipper.
     *
     * <p>Nicht gegen den Betreiber, der wirklich zehn Jahre aufbewahren will: Der traegt
     * 3650 ein und bekommt sie. Die Grenze faengt die Null, die aus einer 30 eine 300
     * macht, und vor allem die Zahl, bei der die Grenzrechnung ueberlaeuft.
     */
    public static final int HOECHSTE_FRIST_TAGE = 3650;

    /**
     * Die Vorgabe: Setzen und Abbauen, gelungen UND abgewiesen - aber nicht Benutzen.
     *
     * <p>Setzen und Abbauen sind die Taten, nach denen ueberhaupt gefragt wird ("wer hat
     * mein Haus abgerissen"). Die abgewiesenen gehoeren dazu, denn sie sind der Fund, bei
     * dem gar nichts passiert ist - ein Dutzend Fehlversuche an einer fremden Parzelle.
     *
     * <p>{@code benutzen} fehlt mit Absicht: Es ist die mit Abstand haeufigste Tat, und
     * ein Spieler, der eine Minute durch sein Haus laeuft, fuellt die Tabelle mit
     * Tueroeffnungen, nach denen niemand je fragt. Wer die Truhenfrage beantworten will,
     * gibt sie der Welt, in der sie gestellt wird.
     */
    public static final Auswahl VORGABE_AUSWAHL =
            new Auswahl(EnumSet.of(Blocktat.SETZEN, Blocktat.ABBAUEN), true);

    /**
     * Wie viele Zeilen ein Aufraeumschlag hoechstens loescht.
     *
     * <p>Begrenzt, weil das Aufraeumen im Hauptfaden des Spielservers laeuft - dort, wo
     * die eine Datenbankverbindung haengt. Ein unbegrenztes {@code DELETE} auf der
     * groessten Tabelle des Projekts haelt den Server an, und zwar genau am Tageswechsel,
     * wenn die erste Frist faellig wird und Wochen auf einmal weggehen. Was ein Schlag
     * nicht schafft, nimmt der naechste - die Zeilen laufen nicht weg.
     */
    public static final int VORGABE_HAPPEN = 500;

    /** Die Obergrenze fuer einen Happen - darueber waere es wieder ein Stillstand. */
    public static final int HOECHSTER_HAPPEN = 20_000;

    /**
     * Sechs Bloecke Reichweite.
     *
     * <p>Etwas mehr als die Reichweite, in der ein Spieler bauen kann - ein Moderator
     * steht oft nicht genau an dem Loch, das er untersucht, und soll nicht erst
     * heranlaufen muessen.
     */
    public static final int VORGABE_REICHWEITE = 6;

    /**
     * Hundert Bloecke als Obergrenze.
     *
     * <p>Darueber wird die Frage sinnlos: Ein Strahl ueber hundert Bloecke trifft
     * irgendetwas, und der Moderator wuesste nicht mehr, welchen Block er gefragt hat.
     */
    public static final int HOECHSTE_REICHWEITE = 100;

    /**
     * Zehn Zeilen je Abfrage.
     *
     * <p>So viel, wie der Chat auf einen Blick zeigt. Mehr hiesse, dass die ersten
     * Zeilen nach oben herauslaufen - und das sind bei "neueste zuerst" genau die, auf
     * die es ankommt.
     */
    public static final int VORGABE_ZEILEN = 10;

    /** Hundert Zeilen als Obergrenze - darueber ist der Chat kein Anzeigemittel mehr. */
    public static final int HOECHSTE_ZEILEN = 100;

    /**
     * Die Vorgabe fuer {@link #PFAD_BENUTZBAR}: die Behaelter.
     *
     * <p><b>Eine Weissliste in der Datei und nicht {@code Material.isInteractable()}</b>,
     * und das hat zwei Gruende. Der erste ist handfest: Jene Frage ist in Bukkit VERALTET,
     * und dieses Projekt hat kein einziges {@code SuppressWarnings} - es uebersetzt mit
     * {@code -Xlint:all} ohne eine Warnung, und das soll so bleiben.
     *
     * <p>Der zweite ist der bessere: Jene Frage haelt auch jedes Gartentor, jeden Knopf
     * und jede Leiter fuer interessant. Wer {@code benutzen} einschaltet, will die
     * Truhenfrage beantworten - "wer war in meiner Kiste" -, und ein Buch voller
     * Tueroeffnungen beantwortet sie schlechter, weil die eine Zeile darin untergeht.
     * Welche Bloecke zaehlen, weiss der Betreiber besser als eine Vermutung des Spiels,
     * und darum steht es in seiner Datei.
     *
     * <p>Die Vorgabe nennt, was man abschliesst: Truhen, Faesser, Spinde, Trichter und
     * die Oefen. Nicht die gefaerbten Shulkerkisten - das waeren sechzehn Zeilen fuer
     * einen Block, den man ohnehin mitnimmt.
     */
    public static final Set<String> VORGABE_BENUTZBAR = Set.of(
            "chest", "trapped_chest", "barrel", "shulker_box", "ender_chest",
            "hopper", "dispenser", "dropper", "furnace", "blast_furnace", "smoker",
            "brewing_stand", "beacon", "lectern", "decorated_pot");

    /**
     * Was eine Welt aufschreibt: welche Taten, und ob auch die abgewiesenen.
     *
     * <p><b>Die eine Regel steht in {@link #schreibt}</b>, und sie steht nur dort. Dass
     * {@code abgewiesen} quer zu allen Taten wirkt und nicht selbst eine ist, waere sonst
     * eine Verzweigung, die an jedem Ereigniszweig steht - und beim naechsten Zweig
     * vergessen.
     *
     * @param taten          welche Taten aufgeschrieben werden; leer heisst "keine"
     * @param auchAbgewiesen ob die misslungenen Versuche mitkommen
     */
    public record Auswahl(Set<Blocktat> taten, boolean auchAbgewiesen) {

        public Auswahl {
            if (taten == null) {
                throw new IllegalArgumentException("Nichts ist keine Auswahl von Taten -"
                        + " eine LEERE Menge ist erlaubt und heisst \"hier nichts\"");
            }
            // addAll und NICHT EnumSet.copyOf: Jenes wirft bei einer leeren Sammlung,
            // die selbst kein EnumSet ist ("Collection is empty") - und die leere
            // Auswahl ist hier der erlaubte Fall, der "in dieser Welt nichts" heisst.
            // Ein Wurf traefe genau den Betreiber, der eine Welt bewusst ausnimmt.
            Set<Blocktat> kopie = EnumSet.noneOf(Blocktat.class);
            kopie.addAll(taten);
            taten = Collections.unmodifiableSet(kopie);
        }

        /**
         * Kommt diese Tat ins Buch?
         *
         * <p>Die Reihenfolge der beiden Fragen ist Absicht: Erst die Tat, dann der
         * Ausgang. Eine Welt ohne {@code benutzen} schreibt auch den ABGEWIESENEN
         * Truhenzugriff nicht - sonst waere {@code abgewiesen} eine Hintertuer, die eine
         * Tat zuschaltet, die der Betreiber abgewaehlt hat.
         *
         * @param tat      was getan wurde
         * @param gelungen ob es durchging
         */
        public boolean schreibt(Blocktat tat, boolean gelungen) {
            if (tat == null || !taten.contains(tat)) {
                return false;
            }
            return gelungen || auchAbgewiesen;
        }

        /** Ob diese Auswahl ueberhaupt etwas aufschreibt. */
        public boolean leer() {
            return taten.isEmpty();
        }

        /**
         * Die Auswahl, wie sie in der Datei stuende - fuer den Bericht beim Start.
         *
         * <p>Damit der Betreiber im Protokoll sieht, was WIRKLICH gilt, und nicht nur,
         * was er glaubt eingetragen zu haben. Aus den Daten gebaut, nicht abgeschrieben.
         */
        public String wieEingetragen() {
            List<String> teile = new ArrayList<>();
            for (Blocktat tat : Blocktat.values()) {
                if (taten.contains(tat)) {
                    teile.add(tat.schluessel());
                }
            }
            if (auchAbgewiesen) {
                teile.add(Blocktat.NAME_ABGEWIESEN);
            }
            return teile.isEmpty() ? "[]" : "[" + String.join(", ", teile) + "]";
        }
    }

    private final int fristTage;
    private final int jeAufraeumen;
    private final Auswahl vorgabe;
    private final Map<String, Auswahl> jeWelt;
    private final Set<String> benutzbareBloecke;
    private final int reichweite;
    private final int zeilenJeAbfrage;

    /**
     * @param fristTage         wie lange eine Zeile bleibt
     * @param jeAufraeumen      wie viele Zeilen ein Schlag loescht
     * @param vorgabe           was eine Welt aufschreibt, die nichts eigenes sagt
     * @param jeWelt            die Welten mit eigener Auswahl, nach WELTSCHLUESSEL
     *                          ({@code freimarken:altfurt}) - nicht nach Namen, denn der
     *                          Vergleich im Ereignis fragt
     *                          {@code World.key().asString()}
     * @param benutzbareBloecke welche Bloecke die Tat {@code benutzen} aufschreibt; eine
     *                          leere Menge heisst "keiner", und dann wirkt
     *                          {@code benutzen} in keiner Welt
     * @param reichweite        wie weit der Blick des Befehls reicht, in Bloecken
     * @param zeilenJeAbfrage   wie viele Zeilen eine Abfrage hoechstens zeigt
     */
    public Blockbuchwerte(int fristTage, int jeAufraeumen, Auswahl vorgabe,
                          Map<String, Auswahl> jeWelt, Set<String> benutzbareBloecke,
                          int reichweite, int zeilenJeAbfrage) {
        if (!brauchbareReichweite(reichweite)) {
            throw new IllegalArgumentException("Eine Reichweite von " + reichweite
                    + " Bloecken ist keine - erlaubt ist 1 bis " + HOECHSTE_REICHWEITE);
        }
        if (!brauchbareZeilen(zeilenJeAbfrage)) {
            throw new IllegalArgumentException("Eine Anzeige von " + zeilenJeAbfrage
                    + " Zeilen ist keine - erlaubt ist 1 bis " + HOECHSTE_ZEILEN
                    + ". Eine 0 waere die stille Form von \"der Befehl findet nie"
                    + " etwas\".");
        }
        this.reichweite = reichweite;
        this.zeilenJeAbfrage = zeilenJeAbfrage;
        if (!brauchbareFrist(fristTage)) {
            throw new IllegalArgumentException("Eine Frist von " + fristTage
                    + " Tagen ist keine - erlaubt ist 1 bis " + HOECHSTE_FRIST_TAGE);
        }
        if (!brauchbarerHappen(jeAufraeumen)) {
            throw new IllegalArgumentException("Ein Happen von " + jeAufraeumen
                    + " Zeilen ist keiner - erlaubt ist 1 bis " + HOECHSTER_HAPPEN);
        }
        if (vorgabe == null) {
            throw new IllegalArgumentException("Ohne Vorgabe wuesste eine Welt ohne"
                    + " eigene Liste nicht, was sie aufschreibt");
        }
        if (jeWelt == null) {
            throw new IllegalArgumentException("Nichts ist keine Karte von Welten - eine"
                    + " LEERE ist erlaubt und heisst \"ueberall die Vorgabe\"");
        }
        if (benutzbareBloecke == null) {
            throw new IllegalArgumentException("Nichts ist keine Liste von Bloecken -"
                    + " eine LEERE ist erlaubt und heisst \"benutzen schreibt nichts\"");
        }
        Set<String> kleinGeschrieben = new LinkedHashSet<>();
        for (String name : benutzbareBloecke) {
            if (name != null && !name.isBlank()) {
                kleinGeschrieben.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.benutzbareBloecke = Collections.unmodifiableSet(kleinGeschrieben);
        this.fristTage = fristTage;
        this.jeAufraeumen = jeAufraeumen;
        this.vorgabe = vorgabe;
        Map<String, Auswahl> sauber = new LinkedHashMap<>();
        for (Map.Entry<String, Auswahl> eintrag : jeWelt.entrySet()) {
            if (eintrag.getKey() == null || eintrag.getKey().isBlank()) {
                throw new IllegalArgumentException("Eine Auswahl ohne Welt");
            }
            if (eintrag.getValue() == null) {
                throw new IllegalArgumentException("Die Welt \"" + eintrag.getKey()
                        + "\" hat keine Auswahl - fuer \"nichts\" steht die leere Liste");
            }
            // EIN SCHLUESSEL OHNE NAMENSRAUM IST KEIN SCHLUESSEL, und dieser Wurf ist
            // der Wall gegen den Fehler, der am 09.11.2026 den ganzen Ladenbereich tot
            // legte: Die config.yml schreibt "altfurt", die Welt traegt
            // "freimarken:altfurt", und wer den rohen Namen hierher gibt, baut eine
            // Karte, die NIE trifft - der Vergleich mit World.key().asString() ist
            // immer ungleich. Nichts bricht, nichts wird rot; das Blockbuch bliebe fuer
            // diese Welt einfach stumm leer, und niemand koennte sagen, warum.
            //
            // Ein Wurf und keine stille Vorgabe, obwohl dieses Projekt sonst die sichere
            // Richtung waehlt: Dies ist kein Vertipper des Betreibers - der Leser hat
            // seinen Namen schon gegen welten: geprueft -, sondern ein Fehler im
            // Programm, und zwar an einer Naht. Er darf nie in den Betrieb kommen, und
            // beim Start laut zu scheitern ist die einzige Fassung davon, die auffaellt.
            String schluessel = eintrag.getKey().trim().toLowerCase(Locale.ROOT);
            if (schluessel.indexOf(':') < 0) {
                throw new IllegalArgumentException("\"" + schluessel + "\" ist ein"
                        + " WeltNAME und kein Weltschluessel - erwartet wird die Form"
                        + " \"namensraum:name\", wie World.key().asString() sie liefert"
                        + " (etwa \"freimarken:" + schluessel + "\"). Eine Karte nach"
                        + " Namen trifft im Ereignis NIE, und das Blockbuch bliebe fuer"
                        + " diese Welt stumm leer.");
            }
            sauber.put(schluessel, eintrag.getValue());
        }
        this.jeWelt = Collections.unmodifiableMap(sauber);
    }

    /** Die Werte, die ohne eine Zeile in der {@code config.yml} gelten. */
    public static final Blockbuchwerte VORGABE = new Blockbuchwerte(
            VORGABE_FRIST_TAGE, VORGABE_HAPPEN, VORGABE_AUSWAHL, Map.of(),
            VORGABE_BENUTZBAR, VORGABE_REICHWEITE, VORGABE_ZEILEN);

    /**
     * Was in dieser Welt aufgeschrieben wird.
     *
     * <p>Eine Welt ohne eigene Zeile bekommt die Vorgabe - das ist die Vererbung, und sie
     * ist der Grund, warum eine neue Welt nicht stumm ohne Buch dasteht.
     *
     * @param weltschluessel der Schluessel, wie {@code World.key().asString()} ihn
     *                       liefert
     */
    public Auswahl fuerWelt(String weltschluessel) {
        if (weltschluessel == null) {
            return vorgabe;
        }
        return jeWelt.getOrDefault(weltschluessel.trim().toLowerCase(Locale.ROOT),
                vorgabe);
    }

    /**
     * Kommt diese Tat an diesem Block in dieser Welt ins Buch?
     *
     * <p><b>Die eine Frage, die die Wache stellt - ein Aufruf und keine drei.</b> So
     * liegen alle drei Bedingungen an derselben Stelle: die Vererbung ("keine eigene
     * Liste heisst Vorgabe"), die Regel ueber den Ausgang ("abgewiesen schaltet keine Tat
     * zu") und die Blockliste beim Benutzen. Drei getrennte Fragen waeren drei
     * Verzweigungen an jedem Ereigniszweig - und beim naechsten Zweig vergessen.
     *
     * <p>Die Blockliste gilt NUR fuer {@link Blocktat#BENUTZEN}, und das ist kein
     * Versehen: Beim Setzen und Abbauen ist jeder Block interessant - wer ein Haus
     * abreisst, reisst Bretter ab, keine Truhen. Nur beim Benutzen braucht es die
     * Einschraenkung, weil sonst jeder Rechtsklick auf Erde eine Zeile schriebe.
     *
     * @param block der Blockname, kleingeschrieben; wird nur beim Benutzen gelesen
     */
    public boolean schreibt(String weltschluessel, Blocktat tat, boolean gelungen,
                            String block) {
        if (!fuerWelt(weltschluessel).schreibt(tat, gelungen)) {
            return false;
        }
        return tat != Blocktat.BENUTZEN || istBenutzbar(block);
    }

    /**
     * Ob dieser Block beim Benutzen aufgeschrieben wird.
     *
     * <p>Oeffentlich fuer den Leser, der die Liste im Startbericht nennt - die
     * ENTSCHEIDUNG trifft immer {@link #schreibt}.
     */
    public boolean istBenutzbar(String block) {
        return block != null
                && benutzbareBloecke.contains(block.trim().toLowerCase(Locale.ROOT));
    }

    /** Welche Bloecke die Tat {@code benutzen} aufschreibt. */
    public Set<String> benutzbareBloecke() {
        return benutzbareBloecke;
    }

    /** Wie lange eine Zeile bleibt, in Tagen. */
    public int fristTage() {
        return fristTage;
    }

    /** Wie viele Zeilen ein Aufraeumschlag hoechstens loescht. */
    public int jeAufraeumen() {
        return jeAufraeumen;
    }

    /** Was eine Welt ohne eigene Liste aufschreibt. */
    public Auswahl vorgabe() {
        return vorgabe;
    }

    /** Die Welten mit eigener Auswahl, nach Weltschluessel. */
    public Map<String, Auswahl> jeWelt() {
        return jeWelt;
    }

    /**
     * Ob irgendwo etwas aufgeschrieben wird.
     *
     * <p>Fuer den Bericht beim Start: Ein Blockbuch, das nirgends etwas schreibt, soll es
     * sagen. Stumm abgeschaltet ist genau der Zustand, in dem der Betreiber Monate
     * spaeter nach Zeilen sucht, die es nie gab.
     *
     * <p>Die Vorgabe zaehlt mit, auch wenn keine Welt sie nennt: Jede Welt, die keine
     * eigene Liste hat, folgt ihr - und das sind alle, solange der Abschnitt
     * {@code welten} fehlt.
     *
     * <p><b>Eine Auswahl, die NUR {@code benutzen} nennt, zaehlt bei leerer Blockliste
     * nicht mit</b>, und das ist der Fall, der sonst still durchfiele: Die Welt hat eine
     * Tat eingetragen, die Liste der Bloecke ist aber leer, und damit schreibt sie
     * nichts - eine Einstellung, die nur halb wirkt, muss das sagen.
     */
    public boolean schreibtIrgendwo() {
        if (schreibtEtwas(vorgabe)) {
            return true;
        }
        for (Auswahl auswahl : jeWelt.values()) {
            if (schreibtEtwas(auswahl)) {
                return true;
            }
        }
        return false;
    }

    /** Ob diese Auswahl wirklich etwas aufschreibt - samt der Blockliste. */
    private boolean schreibtEtwas(Auswahl auswahl) {
        if (auswahl.leer()) {
            return false;
        }
        for (Blocktat tat : auswahl.taten()) {
            if (tat != Blocktat.BENUTZEN || !benutzbareBloecke.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Erlaubt ist 1 bis {@link #HOECHSTE_FRIST_TAGE}. */
    public static boolean brauchbareFrist(long tage) {
        return tage >= 1L && tage <= HOECHSTE_FRIST_TAGE;
    }

    /** Erlaubt ist 1 bis {@link #HOECHSTER_HAPPEN}. */
    public static boolean brauchbarerHappen(long zeilen) {
        return zeilen >= 1L && zeilen <= HOECHSTER_HAPPEN;
    }

    /** Wie weit der Blick des Befehls reicht, in Bloecken. */
    public int reichweite() {
        return reichweite;
    }

    /** Wie viele Zeilen eine Abfrage hoechstens zeigt. */
    public int zeilenJeAbfrage() {
        return zeilenJeAbfrage;
    }

    /** Erlaubt ist 1 bis {@link #HOECHSTE_REICHWEITE}. */
    public static boolean brauchbareReichweite(long bloecke) {
        return bloecke >= 1L && bloecke <= HOECHSTE_REICHWEITE;
    }

    /** Erlaubt ist 1 bis {@link #HOECHSTE_ZEILEN}. */
    public static boolean brauchbareZeilen(long zeilen) {
        return zeilen >= 1L && zeilen <= HOECHSTE_ZEILEN;
    }
}
