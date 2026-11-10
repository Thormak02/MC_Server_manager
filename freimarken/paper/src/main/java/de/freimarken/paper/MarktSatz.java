package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /markt}.
 *
 * <p>Neun Dateien statt einer, eine je Bereich: So teilen zwei gleichzeitig arbeitende
 * Haende nie dieselbe Datei. Die Dateien dieses Schrittes sind {@link Markttext} und
 * {@link MarktBefehle}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {betrag}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Dieser Bereich ist am 10.11.2026 zweimal umgebaut worden, und das zweite Mal hat
 * ihn umgedreht.</b> Vormittags fiel {@code /markt verkaufen} weg - verkauft wird am
 * Schild. Nachmittags fiel die TAFEL {@code bundesmarkt.ankauf}, mit einem Satz des
 * Betreibers: "es kann ja nur ge- und verkauft werden, wofuer es ein Schild gibt."
 *
 * <p>Damit zeigt {@code /markt} nicht mehr eine Liste aus der {@code config.yml},
 * sondern die STAENDE aus der Datenbank: wo sie stehen, was sie handeln und zu welchem
 * Preis. Das ist dieselbe Auskunft wie {@code /shop liste} fuer die eigenen Laeden, nur
 * fuer die des Bundes - und darum ist sie gebaut wie jene, samt Seiten.
 *
 * <p><b>Die Materialnamen stehen weiterhin NICHT in der Sprachdatei.</b> Gezeigt wird
 * der Name, den die Ware traegt ({@code STONE}), und das ist derselbe, den der Spieler im
 * Tooltip liest. Vierzig Worte dafuer waeren eine zweite Abschrift einer Liste, die es
 * nicht mehr gibt.
 */
enum MarktSatz implements Satz {

    /**
     * Die Ueberschrift: wie viele Staende, und welche Seite davon.
     *
     * <p>Die Seite steht AUCH DANN da, wenn es nur eine gibt - dieselbe Entscheidung wie
     * bei {@code shop.liste.kopf}: Zwei Koepfe waeren zwei Zeilen in der Vorlage, die
     * auseinandergehen, und wer "Seite 1 von 1" liest, weiss sofort, dass nichts fehlt.
     */
    KOPF("markt.kopf", Art.SATZ, "anzahl", "seite", "seiten"),

    /**
     * Eine Zeile je Stand: Stelle, Menge, Ware und die beiden Preisstuecke.
     *
     * <p>Die STELLE steht vorn, denn das ist der Zweck dieser Liste: Der Bundesmarkt ist
     * ein ORT, und wer wissen will, wo der Stand fuer seinen Stein steht, soll hingehen
     * koennen. Welt UND Koordinaten, weil der Bund Staende in mehreren Welten haben darf.
     *
     * <p>{@code kauf} und {@code verkauf} sind die zwei Wortstuecke darunter ODER
     * NICHTS - dasselbe Mittel wie bei {@code shop.liste.zeile}: Ein leerer Wert
     * verschwindet rueckstandslos, und darum stehen die Trenner in den Stuecken.
     */
    ZEILE("markt.zeile", Art.SATZ, "welt", "x", "y", "z", "menge", "ware", "kauf",
            "verkauf"),

    /**
     * Das Wortstueck fuer den Preis, zu dem KUNDEN KAUFEN - ein Wortstueck, kein Satz.
     *
     * <p><b>Eigene Stuecke und nicht die von {@code shop.liste}</b>, obwohl sie heute
     * dasselbe sagen: Der Bund ist nicht ein Laden unter Laeden, und ein Betreiber soll
     * seine Staende anders beschriften koennen als die Liste eines Spielers. Dass es
     * trotzdem zwei Paare sind, haelt {@code GrenzeTest} ehrlich - ein Stueck, das
     * niemand mehr sendet, wird dort rot.
     */
    KAUF("markt.kauf", Art.WORT, "preis"),

    /** Das Gegenstueck: Kunden VERKAUFEN zu diesem Preis an den Bund. */
    VERKAUF("markt.verkauf", Art.WORT, "preis"),

    /**
     * Der Hinweis auf die naechste Seite - nur, wenn es eine gibt.
     *
     * <p>Er nennt die ZAHL der naechsten Seite: Ein Hinweis, der den Befehl nicht
     * mitgibt, laesst den Spieler raten, wie man blaettert.
     */
    WEITER("markt.weiter", Art.SATZ, "naechste"),

    /** Die gewuenschte Seite gibt es nicht - mit der Zahl der vorhandenen. */
    KEINE_SEITE("markt.keine-seite", Art.SATZ, "seite", "seiten"),

    /**
     * Der Bund hat ueberhaupt keinen Stand.
     *
     * <p>Eine eigene Zeile und keine leere Liste, und sie sagt seit dem 10.11.2026 etwas
     * anderes als vorher: Nicht "die Liste in der config.yml ist leer", sondern "es steht
     * kein Schild". Das ist der Weg, auf dem ein Betreiber den Ankauf abschaltet - und
     * fuer einen Spieler die Auskunft, dass er nicht sucht, sondern wartet.
     */
    LEER("markt.leer", Art.SATZ),

    /** Was heute noch aus Verkaeufen zu holen ist. */
    FREI("markt.frei", Art.SATZ, "betrag"),

    /**
     * Es gibt keinen Tagesdeckel.
     *
     * <p>Ein EIGENER Satz und keine Zahl in {@link #FREI}: "Heute kannst du noch
     * 0 Mark bekommen" waere das Gegenteil der Wahrheit, und eine riesige Zahl laese
     * sich nicht lesen. Dass es den Satz ueberhaupt gibt, ist die Folge der Entscheidung
     * vom 10.11.2026 - {@code bundesmarkt.tagesdeckel: 0} heisst: kein Deckel.
     */
    OHNE_DECKEL("markt.ohne-deckel", Art.SATZ),

    /**
     * Die Staende liessen sich nicht lesen.
     *
     * <p>Ein eigener Satz und nicht die leere Liste: Eine Stoerung, die wie "es steht
     * kein Schild" aussieht, laesst einen Spieler auf einen Markt warten, der dasteht.
     * Dieselbe Entscheidung wie bei {@code shop.liste.gestoert}.
     */
    GESTOERT("markt.gestoert", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    MarktSatz(String pfad, Art art, String... platzhalter) {
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
     * Keiner dieser Saetze darf leer sein.
     *
     * <p>Dieselbe Hausregel wie in {@link ShopSatz}: Jeder ist die EINZIGE Antwort auf
     * eine Tat. Der Trenner zwischen zwei Sortimentsstuecken durfte es bis zum
     * 10.11.2026 - er ist mit dem Sortiment gegangen, und damit auch der einzige Grund,
     * hier zwei Konstruktoren zu haben.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
