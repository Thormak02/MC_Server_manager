package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /freistadt}.
 *
 * <p>Eine Datei je Bereich, wie bei {@link LadenSatz}: So teilen zwei gleichzeitig
 * arbeitende Haende nie dieselbe Datei. Die Nahtdatei dieses Bereichs ist
 * {@link Freistadttext}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}.
 *
 * <p><b>Der Stadtname steht NICHT in der Sprachdatei</b>, dieselbe Entscheidung wie bei
 * den Ladeneinheiten: Er ist Spielereingabe, und dreihundert Staedte braeuchten dort
 * dreihundert Worte. Er geht als Blatt in den Satz - damit bleibt er Text und wird nie
 * zu einem Farbtag, das ein Spieler sich selbst ausgedacht hat.
 */
enum FreistadtSatz implements Satz {

    /** Die Ueberschrift von {@code /freistadt}. */
    KOPF("freistadt.kopf", Art.SATZ),

    /**
     * Eine Zeile der Liste - Name der Stadt, ihr Besitzer und ihre Feldnummer.
     *
     * <p>Die FELDNUMMER steht dabei und nicht die Blockkoordinate: Sie ist kurz, sie ist
     * die Adresse der Stadt, und wer hin will, tippt ohnehin {@code /warp freistadt}.
     */
    ZEILE("freistadt.zeile", Art.SATZ, "name", "besitzer", "feld"),

    /**
     * Es gibt noch keine Freistadt.
     *
     * <p>Eine eigene Zeile und nicht eine leere Liste: Wer nur die Ueberschrift sieht,
     * haelt es fuer einen Fehler. Erreichbar ab Werk - am ersten Tag ist die Welt leer.
     */
    LEER("freistadt.leer", Art.SATZ),

    /**
     * Die Rueckfrage vor dem Gruenden - mit Name, Preis und Guthaben danach.
     *
     * <p>400.000 Mark sind kein Knopfdruck. Dieselbe Bauart wie bei
     * {@code /laden mieten}: Erst zeigen, was es kostet, dann {@code bestaetigen}.
     */
    FRAGE("freistadt.frage", Art.SATZ, "name", "preis", "danach"),

    /** Gegruendet - mit Name, Feldnummer und dem Befehl, der hinfuehrt. */
    GEGRUENDET("freistadt.gegruendet", Art.SATZ, "name", "feld"),

    /**
     * Der Name ist zu kurz oder zu lang.
     *
     * <p>Ein eigener Satz neben {@link #NAME_FALSCH}, kein Sammeltopf: Die Laenge nennt
     * zwei Zahlen, die Zeichen nennen die erlaubten. Wer beides in einen Satz legte,
     * liesse den Spieler raten, welches von beidem gemeint ist.
     */
    NAME_ZU_LANG("freistadt.absage.name-zu-lang", Art.SATZ, "kleinstens", "groesstens"),

    /** Der Name traegt Zeichen, die es nicht geben darf. */
    NAME_FALSCH("freistadt.absage.name-falsch", Art.SATZ),

    /** Der Name gehoert einer Welt - {@code /warp} waere danach mehrdeutig. */
    NAME_RESERVIERT("freistadt.absage.name-reserviert", Art.SATZ, "name"),

    /** Diesen Stadtnamen gibt es schon. */
    NAME_VERGEBEN("freistadt.absage.name-vergeben", Art.SATZ, "name"),

    /** Der Spieler hat so viele Staedte, wie sein Rang erlaubt. */
    GRENZE_ERREICHT("freistadt.absage.grenze-erreicht", Art.SATZ, "hoechstzahl"),

    /**
     * Alle Felder der Welt sind vergeben.
     *
     * <p>Ein eigener Satz neben {@link #GRENZE_ERREICHT}: Jener sagt "du hast genug",
     * dieser sagt "es gibt keine mehr". Wer beides gleich meldete, liesse einen Spieler
     * auf einen Rang sparen, der ihm nichts mehr bringt.
     */
    KEIN_FREIES_FELD("freistadt.absage.kein-freies-feld", Art.SATZ),

    /** Das Guthaben reicht nicht - mit Preis und dem, was fehlt. */
    ZU_WENIG_MARK("freistadt.absage.zu-wenig-mark", Art.SATZ, "preis", "fehlt"),

    /** Die Ablage hat gestoert. Das Geld ist zurueck, gegruendet ist nichts. */
    GESTOERT("freistadt.absage.gestoert", Art.SATZ),

    /** Die Freistaedtewelt fehlt oder ist falsch eingetragen. */
    KEINE_WELT("freistadt.absage.keine-welt", Art.SATZ),

    // --- Die Grundstuecke [Teil C2, 12.11.2026] ---------------------------------

    /**
     * Eine Ecke ist abgesteckt - und was noch fehlt.
     *
     * <p>EIN Satz fuer beide Ecken, mit {@code nummer} und {@code fehlt}: Zwei Saetze
     * waeren derselbe Satz mit einer anderen Zahl, und der Betreiber muesste beide
     * gleich halten.
     */
    ECKE("freistadt.grundstueck.ecke", Art.SATZ, "nummer", "x", "z", "zelle"),

    /** Die dritte Ecke beginnt eine NEUE Absteckung - das muss er lesen. */
    ECKE_NEU("freistadt.grundstueck.ecke-neu", Art.SATZ, "x", "z", "zelle"),

    /** Beide Ecken stehen - jetzt fehlt nur noch das Anlegen. */
    ECKEN_FERTIG("freistadt.grundstueck.ecken-fertig", Art.GEZAEHLT, "zellen"),

    /** Anlegen ohne Absteckung. */
    KEINE_ECKEN("freistadt.grundstueck.keine-ecken", Art.SATZ),

    /** Anlegen mit nur EINER Ecke. */
    EINE_ECKE("freistadt.grundstueck.eine-ecke", Art.SATZ),

    /** Zugeschnitten - mit Zuschnitt und Groesse. */
    ZUGESCHNITTEN("freistadt.grundstueck.zugeschnitten", Art.GEZAEHLT, "zuschnitt",
            "zellen", "bloecke"),

    /** Aufgeloest - die Flaeche faellt an die Stadt zurueck. */
    AUFGELOEST("freistadt.grundstueck.aufgeloest", Art.SATZ, "zuschnitt"),

    /** Die Ueberschrift der Grundstuecksliste. */
    GRUNDSTUECK_KOPF("freistadt.grundstueck.kopf", Art.SATZ, "stadt"),

    /** Eine Zeile der Grundstuecksliste. */
    GRUNDSTUECK_ZEILE("freistadt.grundstueck.zeile", Art.GEZAEHLT, "zuschnitt",
            "besitzer", "zellen"),

    /** Die Stadt hat noch kein Grundstueck. */
    GRUNDSTUECK_LEER("freistadt.grundstueck.leer", Art.SATZ),

    /** Die beiden Ecken liegen nicht im selben Stadtfeld. */
    NICHT_EIN_FELD("freistadt.grundstueck.nicht-ein-feld", Art.SATZ),

    /** Auf diesem Feld steht keine Stadt. */
    KEINE_STADT("freistadt.grundstueck.keine-stadt", Art.SATZ),

    /** Die Stadt gehoert einem anderen. */
    NICHT_DEINE_STADT("freistadt.grundstueck.nicht-deine-stadt", Art.SATZ),

    /** Die Flaeche ueberschneidet sich mit einem vorhandenen Grundstueck. */
    UEBERSCHNEIDUNG("freistadt.grundstueck.ueberschneidung", Art.SATZ),

    /** Mehr Zellen, als die Einstellung erlaubt. */
    ZU_GROSS("freistadt.grundstueck.zu-gross", Art.GEZAEHLT, "zellen", "hoechstens"),

    /** Hier liegt kein Grundstueck - beim Aufloesen. */
    KEIN_GRUNDSTUECK("freistadt.grundstueck.kein-grundstueck", Art.SATZ),

    /** Es gehoert einem anderen - beim Aufloesen. */
    NICHT_DEIN_GRUNDSTUECK("freistadt.grundstueck.nicht-dein-grundstueck", Art.SATZ),

    /** Die Ablage hat gestoert. */
    GRUNDSTUECK_GESTOERT("freistadt.grundstueck.gestoert", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    FreistadtSatz(String pfad, Art art, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return art;
    }

    @Override
    public Set<String> platzhalter() {
        return platzhalter;
    }

    /**
     * Kein Satz dieses Bereichs darf leer sein.
     *
     * <p>Jeder ist eine ganze Zeile an einen Spieler. Eine leere Absage waere ein Befehl,
     * der nichts tut und nichts sagt - und 400.000 Mark, von denen der Spieler nicht
     * weiss, ob sie weg sind.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
