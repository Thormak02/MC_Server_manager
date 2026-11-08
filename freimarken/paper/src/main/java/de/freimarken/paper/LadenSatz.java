package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /laden}.
 *
 * <p>Zwoelf Dateien statt einer, eine je Bereich: So teilen zwei gleichzeitig arbeitende
 * Haende nie dieselbe Datei. Die Datei dieses Schrittes ist {@link Ladentext}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {name}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die Eigenheit dieses Bereichs: Die Namen der Einheiten stehen NICHT in der
 * Sprachdatei.</b> Dieselbe Entscheidung wie bei den Materialnamen des Bundesmarktes und
 * bei der Lohnliste: Zwanzig Staende braeuchten dort zwanzig Worte - eine zweite Abschrift
 * der Liste aus der {@code config.yml}, und zwei Abschriften derselben Tabelle laufen
 * auseinander. Gezeigt wird darum der Name, den der Betreiber eintraegt und den ein
 * Spieler in {@code /laden mieten <name>} auch tippt.
 */
enum LadenSatz implements Satz {

    /** Die Ueberschrift von {@code /laden liste}. */
    KOPF("laden.kopf", Art.SATZ),

    /**
     * Eine Zeile der Liste - Name der Einheit und wo ihre Truhe steht.
     *
     * <p>Der ORT steht dabei, und das ist mehr als Hoeflichkeit: Eine Halle mit zwanzig
     * gleich aussehenden Staenden ist ohne Koordinate nicht zu durchsuchen, und der
     * Spieler soll hingehen koennen, bevor er mietet.
     */
    ZEILE("laden.zeile", Art.SATZ, "name", "ort"),

    /**
     * Es gibt noch keine Ladeneinheiten.
     *
     * <p>Eine eigene Zeile und nicht eine leere Liste: Ein Spieler, der nur die
     * Ueberschrift sieht, haelt es fuer einen Fehler. Erreichbar, solange die Markthalle
     * nicht gebaut ist - also ab Werk.
     */
    LEER("laden.leer", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    LadenSatz(String pfad, Art art, String... platzhalter) {
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
     * <p>Jeder ist eine ganze Zeile an einen Spieler. Eine leere Ueberschrift liesse die
     * Liste ohne Anfang, eine leere Zeile verschwiege einen Stand, und ein leerer
     * Leer-Satz machte {@code /laden liste} zu einem Befehl, der nichts tut.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
