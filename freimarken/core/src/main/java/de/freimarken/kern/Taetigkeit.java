package de.freimarken.kern;

import java.util.Locale;

/**
 * Was ein Spieler getan hat, um Geld zu verdienen.
 *
 * <p><b>Eine Aufzaehlung und keine Liste in der Einstellungsdatei</b> - anders als die
 * Berufe. Der Unterschied ist handfest: Ein neuer BERUF ist ein Eintrag in der
 * {@code config.yml}, eine neue TAETIGKEIT braucht einen eigenen Ereigniszweig im
 * Programm. Was man nicht eintragen kann, soll auch nicht aussehen wie ein Eintrag.
 *
 * <p><b>Die dritte Ebene der Lohnliste heisst je Taetigkeit etwas anderes</b>, und das ist
 * der Grund fuer {@link Namensart}: Unter {@code abbauen} steht ein Block, unter
 * {@code toeten} eine Wesensart. Welche Art es ist, steht HIER und nicht beim Leser - so
 * ist eine neue Taetigkeit eine Konstante mit zwei Angaben und nicht ein Eintrag plus eine
 * vergessene Zuordnung. Dieselbe Entscheidung wie bei den Spezialisierungen am Grundberuf.
 *
 * <p>Der Schluessel ist kleingeschrieben: Er steht in der {@code config.yml} als
 * Ebenenname zwischen Beruf und Material.
 */
public enum Taetigkeit {

    /**
     * Einen gewachsenen Block abgebaut.
     *
     * <p>GEWACHSEN, nicht gesetzt: Ein markierter Block zahlt nichts. Das ist die Regel
     * "ein Block zahlt nur einmal", und sie steht in {@link Blockmarken}.
     */
    ABBAUEN("abbauen", Namensart.MATERIAL),

    /**
     * Einen Block gesetzt.
     *
     * <p>Zahlt nur die ERSTE Setzung einer Position - danach traegt sie eine Marke, und
     * die bleibt. Sonst waere Setzen-Abbauen-Setzen eine Gelddruckmaschine.
     */
    SETZEN("setzen", Namensart.MATERIAL),

    /**
     * Eine reife Pflanze geerntet.
     *
     * <p><b>Fragt die Marke GENAUSO wie das Abbauen</b>, und das ist die wichtigste
     * Entscheidung dieser Konstante. Der naheliegende Entwurf - "Ernten fragt die Marke
     * nicht, weil Saat gesetzt wird" - macht einen einzigen Kuerbis in einem
     * Ein-Block-Loch zur Maschine: setzen (zahlt einmal), abbauen (die Ernte zahlt),
     * wieder setzen (Marke steht, zahlt nichts), wieder abbauen (die Ernte zahlt erneut) -
     * unbegrenzt, ohne Farm, ohne Werkzeug, ohne Weg.
     *
     * <p>Dass ein von Hand gesaetes Feld trotzdem zahlt, besorgt die andere Seite: SAATGUT
     * wird beim Setzen NICHT markiert ({@link Verdienstwerte#PFAD_SAATGUT}). Die Regel
     * bleibt damit eine - "ein Block zahlt nur einmal" -, und die Ausnahme steht an genau
     * einer Stelle und in der Einstellungsdatei.
     *
     * <p>Eine eigene Taetigkeit und nicht ein hoeherer Satz unter {@code abbauen}: Eine
     * reife Pflanze bringt anderes ein als der Boden darunter. Derselbe Block zahlt
     * trotzdem NIE zweimal - welche der beiden gilt, entscheidet der Ereigniszweig an
     * einer Stelle.
     */
    ERNTEN("ernten", Namensart.MATERIAL),

    /**
     * Einen Fisch gefangen.
     *
     * <p>Der Name ist der gefangene GEGENSTAND und nicht das Wesen am Haken: Ein gehakter
     * Tintenfisch ist kein Fang, und Schrott und Schaetze tragen ihren eigenen Satz.
     */
    FISCHEN("fischen", Namensart.MATERIAL),

    /**
     * Ein Wesen getoetet.
     *
     * <p>Nur ein NATUERLICH entstandenes Wesen zahlt - welche Entstehungsgruende das sind,
     * steht in {@link Verdienstwerte#PFAD_SPAWNGRUENDE}. Das ist die Marke fuer Wesen:
     * Ohne sie zahlte ein gezuechtetes Kalb zweimal (einmal die Zucht, einmal der Tod) und
     * eine Spawnerfarm so oft, wie sie spawnt.
     */
    TOETEN("toeten", Namensart.WESEN),

    /**
     * Zwei Wesen gezuechtet.
     *
     * <p>Der Name ist das JUNGE und nicht ein Elternteil - bei Pferd und Esel also das
     * Maultier, das selbst nie wieder zuchtfaehig ist.
     */
    ZUECHTEN("zuechten", Namensart.WESEN);

    /**
     * Was die dritte Ebene der Lohnliste benennt.
     *
     * <p>Zwei und nicht drei: Ein BLOCK und ein GEGENSTAND sind beide ein
     * {@code Material}, und ob ein Material auch ein Block ist, laesst sich ohne laufenden
     * Server nicht fragen - {@code Material.isBlock()} und {@code isItem()} schlagen in
     * der Registry nach und werfen. Gemessen gegen das echte Jar, nicht vermutet. Eine
     * eigene Blockliste im Programm waere eine zweite Abschrift des Spiels, und die laeuft
     * mit jeder Fassung weiter auseinander. Darum steht hier nur die Unterscheidung, die
     * ohne Server haltbar ist.
     */
    public enum Namensart {

        /** Ein Block oder ein Gegenstand - geprueft gegen {@code org.bukkit.Material}. */
        MATERIAL,

        /** Eine Wesensart - geprueft gegen {@code org.bukkit.entity.EntityType}. */
        WESEN
    }

    private final String schluessel;
    private final Namensart namensart;

    Taetigkeit(String schluessel, Namensart namensart) {
        this.schluessel = schluessel;
        this.namensart = namensart;
    }

    /** Der Ebenenname in der Einstellungsdatei. */
    public String schluessel() {
        return schluessel;
    }

    /** Was die Ebene darunter benennt - danach prueft der Leser die Namen. */
    public Namensart namensart() {
        return namensart;
    }

    /**
     * Die Taetigkeit zu einem Schluessel, oder {@code null} fuer einen unbekannten.
     *
     * <p>Kein Wurf: Der Leser der Einstellungsdatei fragt damit, ob eine Ebene eine
     * Taetigkeit ist, und eine unbekannte ist dort eine KLAGE mit Zeilennummer und kein
     * Absturz beim Start.
     */
    public static Taetigkeit ausSchluessel(String text) {
        if (text == null) {
            return null;
        }
        String klein = text.trim().toLowerCase(Locale.ROOT);
        for (Taetigkeit taetigkeit : values()) {
            if (taetigkeit.schluessel.equals(klein)) {
                return taetigkeit;
            }
        }
        return null;
    }
}
