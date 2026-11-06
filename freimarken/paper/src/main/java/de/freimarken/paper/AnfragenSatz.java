package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /tpa}, {@code /tpahere}, {@code /tpaccept} und {@code /tpdeny}.
 *
 * <p>Acht Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig
 * arbeitende Haende nie dieselbe Datei. Die Datei dieses Schrittes ist
 * {@code AnfragenBefehle}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {name}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Der Eigenheit dieses Bereichs sind ZWEI Empfaenger in einem Zweig.</b>
 * {@code /tpa} schickt eine Zeile an den Fragenden und zwei an den Angefragten,
 * {@code /tpaccept} an beide - und jeder Satz traegt genau EINEN Namen, naemlich den des
 * ANDEREN. Darum stehen vier Schluessel bewusst paarweise benannt
 * ({@link #ANNEHMEN_DU_KOMMST} / {@link #ANNEHMEN_ER_KOMMT} und die zwei
 * {@code QUITTUNG_}): So sieht man im Baum, dass es zwei Paare und nicht vier freie
 * Saetze sind. Welcher davon an WEN geht, nagelt {@code GrenzeTest} 31e fest.
 *
 * <p><b>{@link #NIEMAND} steht eine Ebene hoeher als die uebrigen</b>, naemlich
 * unmittelbar unter {@code anfrage} und nicht unter {@code anfrage.annehmen}: Er gehoert
 * zu {@code /tpaccept} UND zu {@code /tpdeny}, und beide lesen zeichengleich dasselbe.
 * Zwei Schluessel waeren derselbe Satz an zwei Orten - der Betreiber aenderte einen und
 * wunderte sich, warum der andere Befehl weiter das Alte sagt. Keine Probe faende das je:
 * {@code GrenzeTest} 30 bliebe gruen, weil beide Schluessel ja benutzt wuerden.
 */
enum AnfragenSatz implements Satz {

    // --- Was zu mehr als einem Befehl gehoert ---------------------------------

    /**
     * Es steht keine Anfrage offen - von {@code /tpaccept} UND von {@code /tpdeny}.
     *
     * <p>Kein {@code .eins}/{@code .viele} von {@link #ABLEHNEN_ABGELEHNT}: Null ist hier
     * keine Zaehlform, sondern ein anderer Sachverhalt. "0 Anfragen abgelehnt." waere
     * eine Zeile, die es heute nicht gibt.
     */
    NIEMAND("anfrage.niemand", Art.SATZ),

    // --- /tpa und /tpahere - die Frage stellen --------------------------------

    /**
     * Den gibt es nicht, oder er ist unsichtbar - EIN Satz fuer beide Faelle.
     *
     * <p>{@code {name}} ist das GETIPPTE Wort und nicht der echte Name: Bei einem
     * unsichtbaren Spieler waere der richtige Name ein Verrat, und zwei Saetze - einer
     * fuer "gibt es nicht", einer fuer "ist unsichtbar" - verrieten ihn genauso.
     */
    FRAGE_NICHT_DA("anfrage.frage.nicht-da", Art.SATZ, "name"),

    /** Er hat sich selbst gefragt. Ohne Namen: Er kennt seinen eigenen. */
    FRAGE_BEI_DIR("anfrage.frage.bei-dir", Art.SATZ),

    /** Dieselbe Anfrage steht schon offen - es ist nichts hinzugekommen. */
    FRAGE_STEHT_NOCH("anfrage.frage.steht-noch", Art.SATZ, "name"),

    /** Die Quittung an den FRAGENDEN - mit dem Namen des Angefragten. */
    FRAGE_GESTELLT("anfrage.frage.gestellt", Art.SATZ, "name"),

    /**
     * Die Frage an den Angefragten, Zweig {@code /tpahere} - mit dem Namen des
     * FRAGENDEN.
     *
     * <p>Ein eigener Schluessel und kein Wortstueck in {@link #FRAGE_MOECHTE_KOMMEN}:
     * Die zwei Formen unterscheiden sich im ganzen Praedikat, und es sind zwei
     * verschiedene Sachverhalte - kein Einzahl gegen Mehrzahl derselben Sache.
     */
    FRAGE_EINGELADEN("anfrage.frage.eingeladen", Art.SATZ, "name"),

    /** Dieselbe Frage, Zweig {@code /tpa} - er will selbst kommen. */
    FRAGE_MOECHTE_KOMMEN("anfrage.frage.moechte-kommen", Art.SATZ, "name"),

    /**
     * Die zweite Zeile an den Angefragten - wie er antwortet und wie lange er Zeit hat.
     *
     * <p>Ein eigener Schluessel und keine Umbruchmarke an den zwei Saetzen darueber:
     * Die Zeile traegt eine ANDERE Farbe, und ein Satz mit Umbruch sendet beide Zeilen
     * in derselben. Sie haengt ausserdem an BEIDEN Zweigen.
     */
    FRAGE_HINWEIS("anfrage.frage.hinweis", Art.SATZ),

    // --- /tpaccept - annehmen -------------------------------------------------

    /** Wer gefragt hatte, ist inzwischen abgemeldet. */
    ANNEHMEN_FRAGENDER_WEG("anfrage.annehmen.fragender-weg", Art.SATZ),

    /**
     * An den ANNEHMER, wenn er selbst wandert ({@code /tpahere}) - mit dem Namen des
     * Fragenden.
     */
    ANNEHMEN_DU_KOMMST("anfrage.annehmen.du-kommst", Art.SATZ, "name"),

    /** An den ANNEHMER, wenn der Fragende wandert ({@code /tpa}). */
    ANNEHMEN_ER_KOMMT("anfrage.annehmen.er-kommt", Art.SATZ, "name"),

    /**
     * An den FRAGENDEN, wenn er selbst wandert ({@code /tpa}) - mit dem Namen des
     * Annehmers.
     *
     * <p>Das Gegenstueck zu {@link #ANNEHMEN_ER_KOMMT}: dasselbe Ereignis, der andere
     * Empfaenger. Der Name ist darum der jeweils ANDERE, und ein vertauschter Empfaenger
     * liest "Du kommst zu dir selbst".
     */
    ANNEHMEN_QUITTUNG_DU_KOMMST("anfrage.annehmen.quittung-du-kommst", Art.SATZ, "name"),

    /** An den FRAGENDEN, wenn der Annehmer wandert ({@code /tpahere}). */
    ANNEHMEN_QUITTUNG_ER_KOMMT("anfrage.annehmen.quittung-er-kommt", Art.SATZ, "name"),

    /**
     * Wie viele Anfragen nach dem Annehmen noch warten - an den ANNEHMER.
     *
     * <p>{@link Art#GEZAEHLT}, weil bis zu diesem Schritt ein Ternaer im Programm
     * zwischen " Anfrage." und " Anfragen." entschied - beide Formen standen also schon
     * im Spiel, und es musste keine erfunden werden. Die Wahl trifft jetzt
     * {@code Sprachbuch.wortlaut} an {@code anzahl == 1L}.
     *
     * <p><b>Keine Nullform</b>: Bei Null wird gar nicht gesendet, und das bleibt so
     * ({@code AnfragenBefehle.sendeAnnahme}, Wache {@code weitere > 0} - gehalten von
     * {@code AufrufstelleTest} 34n mit den Faellen Null und Eins). {@code Art.GEZAEHLT}
     * kennt nur eins und viele - derselbe Fall, der bei
     * {@code ParzellenSatz.AUFWAND_VERSCHWINDET} schon beschrieben ist.
     */
    ANNEHMEN_WEITERE("anfrage.annehmen.weitere", Art.GEZAEHLT, "anzahl"),

    // --- /tpdeny - ablehnen ---------------------------------------------------

    /**
     * Wie viele Anfragen weggenommen wurden.
     *
     * <p>Der GANZE Satz steht in jedem der zwei Blaetter und nicht nur das Zaehlwort:
     * Sonst kann der Betreiber ihn faerben, aber nicht umstellen. Die Nullform ist
     * {@link #NIEMAND} und nicht ein drittes Blatt.
     */
    ABLEHNEN_ABGELEHNT("anfrage.ablehnen.abgelehnt", Art.GEZAEHLT, "anzahl");

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    AnfragenSatz(String pfad, Art art, String... platzhalter) {
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

    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
