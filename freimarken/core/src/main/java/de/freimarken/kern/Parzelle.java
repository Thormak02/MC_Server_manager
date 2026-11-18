package de.freimarken.kern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Stueck vergebener Besitz - eine Parzelle in der Marktstadt oder eine Freistadt.
 *
 * <p>Der Name sagt heute weniger, als die Zeile traegt, und das ist Absicht: Alle Arten
 * liegen in derselben Tabelle, siehe {@link Art}. Umbenennen hiesse, 14 Aufrufstellen in
 * sieben Dateien anzufassen, ohne dass dabei eine einzige Regel besser wuerde.
 *
 * <p>Die Flaeche steht nicht als Koordinatenpaar drin, sondern als Parzellennummer - die
 * Umrechnung in Weltkoordinaten macht der {@link Rasterplan}. Damit gibt es genau eine
 * Stelle, die weiss, wie gross eine Parzelle ist, und ein spaeteres Umstellen des Rasters
 * laesst bestehende Datensaetze unberuehrt.
 *
 * <p>Die Welt steht als SCHLUESSEL (``freimarken:marktstadt``), nicht als Name. Namen sind
 * in Paper seit 26.1 nicht mehr der Bezugspunkt, und ein Fremdschluessel auf einen Namen
 * muss spaeter wandern.
 *
 * @param id          laufende Nummer, vom Lager vergeben
 * @param welt        Weltschluessel, z.B. {@code freimarken:marktstadt}
 * @param x           Parzellennummer auf der X-Achse
 * @param z           Parzellennummer auf der Z-Achse
 * @param eigentuemer wem sie gehoert
 * @param mitbauer    wer ausserdem bauen darf, nie {@code null}
 * @param preis       was sie gekostet hat, in Mark
 * @param erworbenAm  wann sie vergeben wurde
 * @param art         was fuer ein Stueck Besitz das ist
 * @param elternId    Nummer des uebergeordneten Besitzes, sonst {@link #KEIN_ELTERNTEIL}
 * @param zustand     ob der Besitz laeuft, gekuendigt oder verfallen ist
 * @param name        der Name einer Freistadt, sonst {@link #KEIN_NAME}
 * @param buergerrechte was die Stadtbuerger einer Freistadt im Gemeinschaftsgebiet
 *                      duerfen - bei allem anderen leer, nie {@code null}
 * @param kasse       die Kontonummer der Stadtkasse einer Freistadt, sonst
 *                    {@link #KEINE_KASSE}
 * @param regeln      der Regeltext einer Freistadt - was Besucher dort beachten
 *                    sollen -, sonst {@link #KEINE_REGELN}
 * @param besucht     der TAG des letzten Besuchs einer Freistadt, sonst
 *                    {@link #NIE_BESUCHT}. Ein {@link LocalDate} und kein
 *                    {@code Instant}: Gefragt ist der Tag, und ein Zeitpunkt haette
 *                    eine zweite Zone - dieselbe Entscheidung wie bei
 *                    {@code Bonusstand.letzterTag} und {@code Mietvertrag.beginn}
 */
public record Parzelle(
        long id,
        String welt,
        int x,
        int z,
        UUID eigentuemer,
        Set<UUID> mitbauer,
        long preis,
        Instant erworbenAm,
        Art art,
        long elternId,
        Zustand zustand,
        String name,
        Set<Tat> buergerrechte,
        UUID kasse,
        String regeln,
        LocalDate besucht) {

    /**
     * Keine Elternnummer.
     *
     * <p>Null ist dafuer frei: AUTO_INCREMENT beginnt bei 1, und das Speicherlager zaehlt
     * ebenfalls ab 1. Damit kostet die Umwandlung aus der Datenbank nichts, denn
     * {@code ResultSet.getLong} liefert fuer SQL NULL genau diese Null.
     */
    public static final long KEIN_ELTERNTEIL = 0L;

    /**
     * Kein Name - alles ausser einer Freistadt.
     *
     * <p>{@code null} und keine leere Zeichenkette: In der Spalte steht NULL, und nur
     * NULL zaehlt InnoDB im eindeutigen Schluessel {@code (welt, name)} als verschieden.
     * Mit einer leeren Zeichenkette gaebe es genau eine namenlose Parzelle je Welt, und
     * die zweite liefe in eine Schluesselverletzung, die niemand erwartet.
     */
    public static final String KEIN_NAME = null;

    /**
     * Keine Buergerrechte - die Vorgabe, und bei allem ausser einer Freistadt die
     * einzige erlaubte Besetzung.
     *
     * <p>Eine LEERE Menge und kein {@code null}: "niemand darf etwas" ist eine Aussage,
     * "ich weiss es nicht" waere keine - und auf dem heissen Pfad muss die Wache sofort
     * antworten koennen.
     */
    public static final Set<Tat> KEINE_RECHTE = Set.of();

    /**
     * Keine Stadtkasse - alles ausser einer Freistadt.
     *
     * <p>Benannt und nicht als nacktes {@code null} in vierzehn Aufrufen: Wer
     * {@code KEINE_KASSE} liest, weiss, dass hier eine Entscheidung steht und nicht ein
     * vergessenes Argument.
     */
    public static final UUID KEINE_KASSE = null;

    /**
     * Kein Regeltext - jede Parzelle, und jede Stadt, die noch keinen hat.
     *
     * <p>Anders als bei der Kontonummer ist das Fehlen hier KEIN Fehler, sondern der
     * Anfangszustand: Eine frisch gegruendete Stadt hat noch keine Regeln, und genau
     * das ist der Grund, aus dem Teil E die Auflage daran haengt.
     */
    public static final String KEINE_REGELN = null;

    /**
     * Noch nie besucht - und alles, was keine Freistadt ist.
     *
     * <p><b>{@code null} und nicht ein fruehes Datum</b>: "Noch nie" und "am 1.1.1970"
     * sind verschiedene Aussagen, und die Verfallsregel aus Plan 16 muss sie
     * unterscheiden koennen. Eine frisch gegruendete Stadt steht hier, bis ihr erster
     * Besucher kommt - und ihre Frist laeuft ab der GRUENDUNG, nicht ab 1970.
     */
    public static final LocalDate NIE_BESUCHT = null;

    /** Die laengste erlaubte Laenge - dieselbe wie die Spaltenbreite. */
    public static final int REGELN_GROESSTENS = 256;

    public Parzelle {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Weltschluessel fehlt");
        }
        if (eigentuemer == null) {
            throw new IllegalArgumentException("Eigentuemer fehlt");
        }
        if (art == null) {
            throw new IllegalArgumentException("Art fehlt");
        }
        if (zustand == null) {
            throw new IllegalArgumentException("Zustand fehlt");
        }
        if (elternId < 0L) {
            throw new IllegalArgumentException("Elternnummer ist negativ: " + elternId);
        }
        // Der erste Teil der Bedingung ist noetig, nicht nur sauber: Ohne ihn verwuerfe
        // dieser Waechter jedes Stueck Besitz mit der Nummer 0 und ohne Elternteil - und
        // genau so sieht ein Objekt aus, das noch keine Nummer vom Lager bekommen hat.
        if (elternId != KEIN_ELTERNTEIL && elternId == id) {
            throw new IllegalArgumentException(
                    "Besitz kann nicht sein eigener Elternteil sein: " + id);
        }
        // Der Name gehoert zur Art, und beide Richtungen sind ein Fehler. Eine
        // Freistadt ohne Namen ist ueber keinen Warp erreichbar - 400.000 Mark fuer ein
        // Feld, das niemand findet. Und eine Parzelle MIT Namen belegte einen Namen im
        // eindeutigen Schluessel, ohne dass je eine Stadt dazu entstuende.
        //
        // Hier und nicht nur in der Datenbank: Ein CHECK auf zwei Spalten faellt erst beim
        // Schreiben auf, und zwar als Treiberausnahme ohne Satz. Hier faellt er in der
        // Probe auf, die das Objekt baut.
        if (art == Art.FREISTADT && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Eine Freistadt braucht einen Namen: " + id);
        }
        if (art != Art.FREISTADT && name != null) {
            throw new IllegalArgumentException("Nur eine Freistadt traegt einen Namen,"
                    + " nicht " + art.schluessel() + ": " + name);
        }
        // DIE BUERGERRECHTE GEHOEREN ZUR ART, genau wie der Name: Buerger hat nur eine
        // Freistadt. Ein Recht an einer Marktstadtparzelle waere eine Zeile in
        // freistadt_rechte, die niemand je liest - und ein Betreiber, der sie dort
        // findet, sucht nach einer Wirkung, die es nicht gibt.
        //
        // Die ANDERE Richtung ist kein Fehler: Eine Freistadt OHNE Rechte ist eine, in
        // der Buerger nur ihr eigenes Grundstueck haben. Das ist die Vorgabe.
        if (art != Art.FREISTADT && buergerrechte != null
                && !buergerrechte.isEmpty()) {
            throw new IllegalArgumentException("Nur eine Freistadt hat Stadtbuerger und"
                    + " damit Buergerrechte, nicht " + art.schluessel() + ": "
                    + buergerrechte);
        }
        // DIE KONTONUMMER IST PFLICHT, und zwar in beiden Richtungen - genau wie der
        // Name und anders als die Buergerrechte [Teil E1].
        //
        // Warum eine GESPEICHERTE Nummer und keine aus der id gerechnete: Die id kommt
        // aus AUTO_INCREMENT, und die wird nach einem Neustart WIEDER VERGEBEN. Eine
        // abgeleitete Nummer haette damit das Konto einer entfernten Stadt an die
        // naechste gegeben, die zufaellig dieselbe id bekommt - mitsamt ihrem
        // Buchungsverlauf. Das waere eine Luege ueber Geld, und sie faellt erst Monate
        // spaeter auf.
        //
        // Warum PFLICHT und nicht NULL-faehig mit spaeterem Anlegen: Eine Stadt ohne
        // Konto muesste eines bekommen, sobald das erste Geld fliesst - also mitten im
        // Geldfluss, an der Stelle, an der am wenigsten Platz fuer einen zweiten
        // Schreibweg ist. Mit der Pflicht entsteht die Nummer dort, wo die Stadt
        // entsteht, und nirgends sonst.
        if (art == Art.FREISTADT && kasse == null) {
            throw new IllegalArgumentException("Eine Freistadt braucht die Kontonummer"
                    + " ihrer Stadtkasse: " + id);
        }
        if (art != Art.FREISTADT && kasse != null) {
            throw new IllegalArgumentException("Nur eine Freistadt hat eine Stadtkasse,"
                    + " nicht " + art.schluessel() + ": " + kasse);
        }
        // DER REGELTEXT: nur EINE Richtung, und das unterscheidet ihn von Name und
        // Kontonummer [Teil E2]. Eine Freistadt OHNE Regeln ist der Anfangszustand -
        // jede frisch gegruendete hat keine, und genau daran haengt die Auflage aus
        // Plan 16. Eine Parzelle MIT Regeln dagegen waere ein Text, den niemand je
        // liest: Angezeigt wird er beim Betreten einer Stadt, und auf einer
        // Marktstadtparzelle betritt man keine.
        if (art != Art.FREISTADT && regeln != null) {
            throw new IllegalArgumentException("Nur eine Freistadt hat Regeln, nicht "
                    + art.schluessel() + ": " + regeln);
        }
        // Die LAENGE hier und nicht nur in der Datenbank: Dort waere ein zu langer Text
        // je nach Betriebsart eine Treiberausnahme ohne Satz oder - schlimmer - eine
        // stille Kuerzung auf 256 Zeichen. Ein Stadtbesitzer, dessen Regeln mitten im
        // Wort aufhoeren, sucht den Fehler bei sich.
        if (regeln != null && regeln.length() > REGELN_GROESSTENS) {
            throw new IllegalArgumentException("Der Regeltext hat " + regeln.length()
                    + " Zeichen und damit mehr als die erlaubten "
                    + REGELN_GROESSTENS + ": " + id);
        }
        // DER BESUCHSTAG: nur EINE Richtung, wie beim Regeltext [Teil E4]. Eine
        // Freistadt OHNE Stempel ist der Anfangszustand - frisch gegruendet war noch
        // niemand da. Eine Parzelle MIT Stempel waere ein Tag, den niemand je liest:
        // Gelesen wird er allein von der Verfallsregel, und die gilt nur fuer Staedte.
        if (art != Art.FREISTADT && besucht != null) {
            throw new IllegalArgumentException("Nur eine Freistadt wird besucht, nicht "
                    + art.schluessel() + ": " + besucht);
        }
        // Unveraenderlich kopieren: Sonst kann der Aufrufer die Mitbauer nachtraeglich
        // aendern, ohne dass das Lager etwas davon erfaehrt - und die Datenbank weiss
        // dann etwas anderes als der Speicher. Dasselbe gilt fuer die Rechte, und dort
        // waere die Folge schwerer: ein Recht, das im Speicher gilt und in der Datenbank
        // nicht steht.
        mitbauer = mitbauer == null ? Set.of() : Set.copyOf(mitbauer);
        buergerrechte = buergerrechte == null ? Set.of() : Set.copyOf(buergerrechte);
    }

    /**
     * Besitz ohne Namen - jede Art ausser einer Freistadt.
     *
     * <p>Damit die vierzehn Aufrufstellen in sieben Dateien nicht alle ein {@code null}
     * als zwoelftes Argument mitschleppen muessen. Das ist KEIN stiller Vorgabewert: Der
     * kanonische Konstruktor darueber wirft, wenn eine {@link Art#FREISTADT} ohne Namen
     * ankommt - dieser Weg kann eine Freistadt also gar nicht erzeugen.
     */
    public Parzelle(long id, String welt, int x, int z, UUID eigentuemer,
                    Set<UUID> mitbauer, long preis, Instant erworbenAm, Art art,
                    long elternId, Zustand zustand) {
        this(id, welt, x, z, eigentuemer, mitbauer, preis, erworbenAm, art, elternId,
                zustand, KEIN_NAME, KEINE_RECHTE, KEINE_KASSE, KEINE_REGELN,
                NIE_BESUCHT);
    }

    // KEIN Kurzweg mit Namen und ohne Rechte, und das ist eine Lehre aus dem 11.11.2026:
    // Damals bekam diese Zeile ihr zwoelftes Feld, den Namen - und SpeicherLager baute
    // beim Mitbauer-Setzen eine neue Zeile von Hand zusammen, mit dem kurzen Konstruktor.
    // Der setzte KEIN_NAME, eine Freistadt ohne Namen wirft, und ein Stadtbesitzer konnte
    // keinen einzigen Mitbauer eintragen. Mit einem stillen Kurzweg fuer die Rechte waere
    // derselbe Fehler diesmal LEISE: Die Zeile verlor nur ihre Buergerrechte, geworfen
    // haette nichts, und im Spiel waere nach jedem Mitbauer-Setzen jedes Recht weg.
    //
    // Darum gibt es ihn nicht. Wer eine Freistadt baut, nennt ihre Rechte - und wer eine
    // Zeile aendert, nimmt einen der Wandler weiter unten.

    /** Ob dieser Spieler Eigentuemer ist. */
    public boolean gehoert(UUID spieler) {
        return eigentuemer.equals(spieler);
    }

    /**
     * Ob das eine Meerinsel ist [Schritt 10].
     *
     * <p>Die Gegenfrage zu {@link #istFreistadt}, und beide zusammen machen die Zeile
     * dreiwertig: Bis zum 18.11.2026 war alles, was keine Freistadt war, eine
     * Marktstadtparzelle - sieben Riegel in dieser Klasse fragen so. Eine Insel traegt
     * genauso wenig Namen, Kasse, Regeln und Besuchstag wie eine Parzelle, also mussten
     * jene Riegel NICHT angefasst werden; wer hier aber etwas baut, das nur fuer die
     * Marktstadt gilt, fragt ab jetzt diese Methode und nicht mehr {@code !istFreistadt}.
     */
    public boolean istWerder() {
        return art == Art.WERDER;
    }

    /** Ob das eine Freistadt ist - dann und nur dann traegt die Zeile einen Namen. */
    public boolean istFreistadt() {
        return art == Art.FREISTADT;
    }

    /**
     * Teil eines uebergeordneten Besitzes - dann wird hier nichts einzeln geaendert.
     *
     * <p>Wer eine Gruppe aufloesen oder ihre Mitbauer aendern will, tut das an der Gruppe.
     * Das Kind traegt seinen Eigentuemer und seine Mitbauer zwar selbst - nur so bleibt
     * {@link #darfBauen} eine Abfrage ohne Umweg -, aber geschrieben wird beides
     * ausschliesslich ueber den Elternteil.
     */
    public boolean hatElternteil() {
        return elternId != KEIN_ELTERNTEIL;
    }

    /**
     * Ob die Stadtbuerger dieser Stadt das duerfen.
     *
     * <p>Eine Frage an der Zeile und nicht an einer eigenen Ablage: Die Rechte reisen mit
     * der Stadt, so wie die Mitbauer - und damit hat der heisse Pfad sie schon in der
     * Hand, wenn er die Stadt gefunden hat. Null zusaetzliche Gaenge.
     *
     * <p>Sagt NICHTS darueber, ob der Fragende Buerger ist. Das entscheidet
     * {@link Stadtbuergerschaft#istBuerger}, und die Trennung ist Absicht: Hier steht,
     * was eine Stadt erlaubt, dort, wer dazugehoert.
     */
    public boolean buergerDuerfen(Tat tat) {
        return buergerrechte.contains(tat);
    }

    /**
     * Dieselbe Zeile mit anderen Mitbauern.
     *
     * <p><b>Es gibt ihn wegen eines Fundes</b>, nicht aus Geschmack: Das Speicherlager
     * setzte die neue Zeile beim Mitbauer-Setzen von Hand zusammen und verlor am
     * 11.11.2026 dabei den Namen. Jedes Feld, das diese Zeile kuenftig bekommt, ginge an
     * derselben Stelle wieder verloren - und ab dem dreizehnten Feld lautlos, weil nicht
     * jedes einen Riegel hat wie der Name.
     */
    public Parzelle mitMitbauern(Set<UUID> andere) {
        return new Parzelle(id, welt, x, z, eigentuemer, andere, preis, erworbenAm,
                art, elternId, zustand, name, buergerrechte, kasse, regeln, besucht);
    }

    /**
     * Dieselbe Zeile mit anderen Buergerrechten.
     *
     * <p>Ein Wandler und kein Setzer - die Zeile ist ein Record. Dass es ihn gibt, hat
     * denselben Grund wie {@code Grundstueck.mitAngebot}: Wer den neuen Stand von Hand
     * zusammensetzt, vergisst eines Tages ein Feld, und niemand sieht es.
     */
    public Parzelle mitBuergerrechten(Set<Tat> andere) {
        return new Parzelle(id, welt, x, z, eigentuemer, mitbauer, preis, erworbenAm,
                art, elternId, zustand, name, andere, kasse, regeln, besucht);
    }

    /**
     * Dieselbe Zeile mit anderem Zustand - fuer den Verfall und die Wiederbelebung.
     *
     * <p>Es gibt BEWUSST keinen Wandler fuer die Kontonummer. Eine Kontonummer, die
     * sich aendern laesst, ist keine: Das Guthaben liegt unter der alten, und wer sie
     * umschreibt, macht es unerreichbar, ohne dass eine Zeile im Buch davon erzaehlt.
     * Sie entsteht mit der Stadt und vergeht mit ihr.
     */
    public Parzelle mitZustand(Zustand anderer) {
        return new Parzelle(id, welt, x, z, eigentuemer, mitbauer, preis, erworbenAm,
                art, elternId, anderer, name, buergerrechte, kasse, regeln, besucht);
    }

    /**
     * Dieselbe Zeile mit einem neuen Besuchstag - fuer den Stempel aus Teil E4.
     *
     * <p>Der Tag kommt HEREIN und wird hier nicht gerechnet: Dieser Satz kennt keine
     * Uhr und keine Zone, und genau das soll so bleiben. Wer stempelt, hat "heute"
     * schon ueber {@code Tagesgrenze.tagVon} bestimmt.
     */
    public Parzelle mitBesuch(LocalDate tag) {
        return new Parzelle(id, welt, x, z, eigentuemer, mitbauer, preis, erworbenAm,
                art, elternId, zustand, name, buergerrechte, kasse, regeln, tag);
    }

    /** Dieselbe Zeile mit anderem Regeltext. */
    public Parzelle mitRegeln(String andere) {
        return new Parzelle(id, welt, x, z, eigentuemer, mitbauer, preis, erworbenAm,
                art, elternId, zustand, name, buergerrechte, kasse, andere, besucht);
    }

    /**
     * Ob diese Stadt einen Regeltext hat.
     *
     * <p>Die eine Stelle, die das entscheidet - und sie fragt nicht nur auf
     * {@code null}, sondern auch auf Leerraum: Ein Text aus drei Leerzeichen ist keine
     * Regel, und ohne diese Frage waere er eine erfuellte Auflage.
     */
    public boolean hatRegeln() {
        return regeln != null && !regeln.isBlank();
    }

    /**
     * Ob dieser Spieler hier bauen darf - als Eigentuemer oder als Mitbauer.
     *
     * <p>Der Zustand steht vorn, weil er fuer alle gilt: Auf verfallenem Besitz baut auch
     * der Eigentuemer nicht mehr.
     *
     * <p><b>OHNE Spieler ist die Antwort NEIN und kein Wurf</b> [18.11.2026, gefunden
     * von InselbuchTest]: {@code null} heisst hier "frage nur, ob ueberhaupt jemand
     * darf" - genau so fragt eine Wache, wenn sie einen Handbalken begruendet, und
     * genau so kommen die Ereignisse ohne Spieler herein. Vorher warf diese Zeile:
     * {@code Set.of(...).contains(null)} ist eine NullPointerException, und zwar auch
     * bei einer LEEREN Mitbauerliste. Im Spiel haette die Inselwache daraus eine Klage
     * ueber eine stumme Datenbank gemacht und jedes Bauen abgelehnt - mit einem Grund,
     * der nicht stimmt.
     */
    public boolean darfBauen(UUID spieler) {
        return spieler != null && zustand.erlaubtBauen()
                && (gehoert(spieler) || mitbauer.contains(spieler));
    }
}
