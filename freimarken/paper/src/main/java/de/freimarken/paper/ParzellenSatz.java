package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /parzelle} - und die neunzehn Bescheide des Besitzbuchs.
 *
 * <p>Sieben Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig
 * arbeitende Haende nie dieselbe Datei. Die Datei dieses Schrittes ist
 * {@code ParzellenBefehle}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {betrag}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Beide Zuege sind gebaut.</b> Zug 2a brachte {@code info}, {@code nehmen},
 * {@code liste} und {@code absage} samt der Werbezeile; Zug 2b die Zweige
 * {@code mitbauer}, {@code aufgeben}, {@code zusammenlegen}, {@code auftrennen} und
 * {@code aufwand}, dazu die Wortstuecke, die mehrere Zweige teilen. Aus
 * {@code ParzellenBefehle.java} ist damit jede Farbe verschwunden, und {@code GrenzeTest}
 * fuehrt die Datei nicht mehr in der Menge, die Farbe nennen darf - findet die Probe dort
 * noch eine, ist ein Satz nicht umgezogen.
 *
 * <p><b>Die zwei Ausnahmen von Zug 2a sind aufgeloest.</b> Der Wortlaut von
 * {@link #BEZAHLT} und der von {@link #BESCHEID_GRUPPE_ZU_GROSS} wurde vom Zusammenlegen
 * woertlich ein zweites Mal gebaut; beide trugen darum im Kopf der Vorlage die Marke
 * {@code HALB IN KRAFT:}. {@code zusammengelegtZeilen} und {@code gruppeZuGrossZeilen}
 * fragen jetzt dieselben Schluessel, und die beiden Marken sind heraus. Ein eigener
 * Schluessel je Zweig waere der schlimmere Weg gewesen: Dann stuende derselbe Satz zweimal
 * in der Datei, und keine Probe faende das je.
 *
 * <p>Ein Wortstueck, das ein Zweig als WERT in einen Satz gibt, zieht mit um - auch wenn
 * es in einer anderen Methode entsteht (Bauplan 1, die Grenze als SENDEWEG). Das sind hier
 * die vier Taten hinter {@link #STEHT_NICHT_AUF}, die zwei Worte in
 * {@link #BODEN_GELUNGEN} und {@link #MITBAUER_NIEMAND}. Alle sind {@link Art#WORT}: ohne
 * Farbtag, weil ein eingesetzter Wert ein Blatt ist.
 */
enum ParzellenSatz implements Satz {

    // --- Werte und Zeilen, die mehrere Zweige teilen ---------------------------

    /**
     * Das Wort fuer den Preis 0 - als WERT in {@code info.frei}, {@code info.frei-am-limit}
     * und {@code liste.zeile}.
     *
     * <p>Ein {@link Art#WORT} und kein eigener Satz je Stelle: Sonst braeuchte jede der
     * drei Zeilen zwei Fassungen, und der Betreiber muesste "gratis" dreimal aendern.
     */
    GRATIS("parzelle.gratis", Art.WORT),

    /** Dasselbe fuer die erste Parzelle, die ihren Preis mit erklaert. */
    GRATIS_DIE_ERSTE("parzelle.gratis-die-erste", Art.WORT),

    /**
     * Die Rechnung nach einem Kauf - von {@code nehmen} UND vom Zusammenlegen.
     *
     * <p>Ein Schluessel fuer beide Zweige, und das ist der Grund, warum er bis zum zweiten
     * Zug die Marke {@code HALB IN KRAFT:} trug: Zwei Schluessel haetten denselben Satz
     * zweimal in die Datei gestellt, und keine Probe faende das je.
     */
    BEZAHLT("parzelle.bezahlt", Art.SATZ, "betrag", "rest"),

    /**
     * Die Zeile aus der {@code config.yml}, in Gelb - die EINE Stelle, an der sie entsteht.
     *
     * <p>Nur die FARBE ist hier des Betreibers, der Wortlaut steht in der
     * {@code config.yml} unter {@code hinweise.rang-werbung}. In Gelb und nicht in Rot:
     * Die Absage darueber ist die Nachricht, das hier ist das Angebot - zwei rote Zeilen
     * hintereinander liest niemand als zwei Dinge.
     */
    RANG_WERBUNG("parzelle.rang-werbung", Art.SATZ, "werbung"),

    /**
     * Parzellen gibt es nur in einer Welt - die eine Weltpruefung aller Zweige.
     *
     * <p>Sie steht an EINER Stelle im Quelltext ({@code ortInMarktstadt}), weil sie
     * vorher eine freiwillige Zeile am Anfang jeder Methode war und zwei von sieben sie
     * nicht hatten.
     */
    NICHT_IN_DER_MARKTSTADT("parzelle.nicht-in-der-marktstadt", Art.SATZ),

    /**
     * Er steht auf einer anderen Zelle, als der Befehl nennt - mit der Tat, die ausblieb.
     *
     * <p>Die vier Taten sind {@link Art#WORT} und kein Satz je Zweig: Sonst stuende
     * "Du stehst nicht auf Parzelle {x}/{z}." viermal in der Datei, und wer den Satz
     * umstellt, muesste es viermal tun. {@code {tat}} ist dabei ein WERT, also ein Blatt -
     * ein Farbtag darin stuende dem Spieler woertlich im Chat.
     */
    STEHT_NICHT_AUF("parzelle.steht-nicht-auf", Art.SATZ, "x", "z", "tat"),

    TAT_NICHTS_AUFGEGEBEN("parzelle.tat.nichts-aufgegeben", Art.WORT),
    TAT_NICHTS_ZUSAMMENGELEGT("parzelle.tat.nichts-zusammengelegt", Art.WORT),
    TAT_NICHTS_HERAUSGELOEST("parzelle.tat.nichts-herausgeloest", Art.WORT),
    TAT_NICHTS_AUFGETRENNT("parzelle.tat.nichts-aufgetrennt", Art.WORT),

    // --- /parzelle info -------------------------------------------------------

    /**
     * Hier liegt eine Gruppe, wo der Rasterplan noch "Weg" sagt.
     *
     * <p>Ohne diesen Satz hiesse es hier "gehoert allen", waehrend die Schutzwache nebenan
     * fremde Haende abweist - zwei Antworten auf dieselbe Frage.
     */
    INFO_GRUPPENBODEN("parzelle.info.gruppenboden", Art.SATZ),

    INFO_WEG("parzelle.info.weg", Art.SATZ),
    INFO_MAUER("parzelle.info.mauer", Art.SATZ),

    /** Der Kopf: die Nummer. Geht VOR dem Blick in die Ablage hinaus. */
    INFO_KOPF("parzelle.info.kopf", Art.SATZ, "x", "z"),

    INFO_AUSSERHALB("parzelle.info.ausserhalb", Art.SATZ),

    /**
     * Eine freie Parzelle mit der Einladung - nur unter der Hoechstzahl.
     *
     * <p>Zwei Saetze und nicht einer mit Wache: Der Preis bleibt am Limit stehen, die
     * Einladung nicht. Wer vier von vier Parzellen hat, soll ruhig sehen, was die fuenfte
     * kosten WUERDE - aber "/parzelle nehmen" darunter ist eine Aufforderung, die an
     * derselben Stelle mit einer Absage endet.
     */
    INFO_FREI("parzelle.info.frei", Art.SATZ, "preis"),

    /** Dieselbe Parzelle am Limit: der Preis ohne Einladung und ohne Gruen. */
    INFO_FREI_AM_LIMIT("parzelle.info.frei-am-limit", Art.SATZ, "preis"),

    /**
     * Die Absage unter dem Preis - mit zwei Leerzeichen Einzug.
     *
     * <p>Derselbe Wortlaut wie {@link #BESCHEID_GRENZE_ERREICHT}, nur eingerueckt, und
     * darum ein eigener Schluessel: Der Einzug steht IM Satz, also sind die beiden nicht
     * dieselbe Zeichenkette. Sie zu EINEM Schluessel zusammenzuziehen hiesse, an einer der
     * beiden Stellen das heutige Bild zu aendern.
     */
    INFO_AM_LIMIT("parzelle.info.am-limit", Art.GEZAEHLT, "anzahl"),

    /** Wem sie gehoert - von {@code info} und vom Gruppenboden. */
    INFO_EIGENTUEMER("parzelle.info.eigentuemer", Art.SATZ, "name"),

    INFO_GEKAUFT_FUER("parzelle.info.gekauft-fuer", Art.SATZ, "preis"),
    INFO_MITBAUER("parzelle.info.mitbauer", Art.SATZ, "namen"),

    /**
     * Teil einer Gruppe - mit der Felderzahl, aber OHNE Einzahlform.
     *
     * <p>{@link Art#SATZ} und nicht {@link Art#GEZAEHLT}, und das ist Absicht: Heute sagt
     * dieser Satz auch bei einem Feld "Feldern". Eine Einzahlform waere eine inhaltliche
     * Verbesserung und keine Umstellung - sie steht in den offenen Punkten des Bauplans und
     * nicht stillschweigend hier. Bis dahin bleibt das Bild, das heute im Spiel steht.
     */
    INFO_GRUPPE("parzelle.info.gruppe", Art.SATZ, "anzahl"),

    /** Die Spalte dieser Zeile ist nachgezaehlt: acht Fuell-Leerzeichen. */
    INFO_AUFTRENNEN_HINWEIS("parzelle.info.auftrennen-hinweis", Art.SATZ),

    INFO_ZUSAMMENLEGEN_HINWEIS("parzelle.info.zusammenlegen-hinweis", Art.SATZ),

    // --- /parzelle nehmen -----------------------------------------------------

    NEHMEN_ERTEILT("parzelle.nehmen.erteilt", Art.SATZ, "x", "z"),
    NEHMEN_ERSTE_GRATIS("parzelle.nehmen.erste-gratis", Art.SATZ),
    NEHMEN_KEIN_BAULAND("parzelle.nehmen.kein-bauland", Art.SATZ),
    NEHMEN_SCHON_VERGEBEN("parzelle.nehmen.schon-vergeben", Art.SATZ, "name"),
    NEHMEN_GRENZE("parzelle.nehmen.grenze", Art.GEZAEHLT, "anzahl"),
    NEHMEN_ZU_WENIG("parzelle.nehmen.zu-wenig", Art.SATZ, "betrag", "guthaben"),

    /** {@code {von}} und {@code {bis}} kommen zweimal vor - das ist erlaubt und Absicht. */
    NEHMEN_AUSSERHALB("parzelle.nehmen.ausserhalb", Art.SATZ, "von", "bis"),

    /**
     * Der Rest, den dieser Zweig nicht eigens benennt - mit dem Namen des Bescheids.
     *
     * <p>Der Name ist Programmtext und steht darum als WERT im Satz und nicht in ihm: Wer
     * hier liest, soll den Bescheid an einen Operator weitergeben koennen.
     */
    NEHMEN_SONST("parzelle.nehmen.sonst", Art.SATZ, "bescheid"),

    // --- /parzelle liste ------------------------------------------------------

    LISTE_LEER("parzelle.liste.leer", Art.SATZ),
    LISTE_KOPF("parzelle.liste.kopf", Art.SATZ, "anzahl", "hoechstzahl"),

    /**
     * Eine Zeile der Liste - mit zwei Anhaengen, die LEER sein duerfen.
     *
     * <p>{@code {mitbauer}} und {@code {gruppe}} sind entweder das Wortstueck darunter
     * oder nichts. Ein leerer Wert verschwindet rueckstandslos, weil ein eingesetzter Wert
     * ein Blatt ist. Vier Saetze fuer die vier Kombinationen waeren der andere Weg - und
     * der Betreiber muesste den Trenner dann viermal aendern.
     */
    LISTE_ZEILE("parzelle.liste.zeile", Art.SATZ, "x", "z", "preis", "mitbauer", "gruppe"),

    /** Ohne Einzahlform, weil "Mitbauer" im Deutschen keine hat. */
    LISTE_MITBAUER("parzelle.liste.mitbauer", Art.WORT, "anzahl"),

    LISTE_GRUPPE("parzelle.liste.gruppe", Art.WORT),
    LISTE_NAECHSTE("parzelle.liste.naechste", Art.SATZ, "betrag"),

    // --- Die neunzehn Bescheide ----------------------------------------------
    // Welcher Satz zu welchem Bescheid gehoert, entscheidet Sprachschluessel.bescheid -
    // nicht der Sendeort. Darum werden diese Konstanten nirgends namentlich getippt, und
    // darum traegt ihre Deckung Sprachschluessel.familienDeckung und die Zweigprobe.

    BESCHEID_ERTEILT("parzelle.bescheid.erteilt", Art.SATZ),
    BESCHEID_KEINE_PARZELLE("parzelle.bescheid.keine-parzelle", Art.SATZ),
    BESCHEID_NOCH_FREI("parzelle.bescheid.noch-frei", Art.SATZ),

    /**
     * Derselbe Bescheid, wenn er schon am Limit steht - der Grund fuer den zweiten
     * Parameter von {@code Sprachschluessel.bescheid}.
     *
     * <p>Ein Bescheid, zwei Saetze: Das ist der Fall, den eine Probe ueber
     * {@code values()} allein nicht faende.
     */
    BESCHEID_NOCH_FREI_AM_LIMIT("parzelle.bescheid.noch-frei-am-limit", Art.GEZAEHLT,
            "anzahl"),

    BESCHEID_SCHON_VERGEBEN("parzelle.bescheid.schon-vergeben", Art.SATZ),
    BESCHEID_AUSSERHALB("parzelle.bescheid.ausserhalb", Art.SATZ),

    /** Mit der Zahl: "nicht mehr" laesst offen, wie viele es denn sind. */
    BESCHEID_GRENZE_ERREICHT("parzelle.bescheid.grenze-erreicht", Art.GEZAEHLT, "anzahl"),

    BESCHEID_ZU_WENIG_MARK("parzelle.bescheid.zu-wenig-mark", Art.SATZ),
    BESCHEID_NICHT_EIGENTUEMER("parzelle.bescheid.nicht-eigentuemer", Art.SATZ),
    BESCHEID_SCHON_EINGETRAGEN("parzelle.bescheid.schon-eingetragen", Art.SATZ),
    BESCHEID_NICHT_EINGETRAGEN("parzelle.bescheid.nicht-eingetragen", Art.SATZ),
    BESCHEID_TEIL_EINER_GRUPPE("parzelle.bescheid.teil-einer-gruppe", Art.SATZ),
    BESCHEID_NACHBAR_FREI("parzelle.bescheid.nachbar-frei", Art.SATZ),
    BESCHEID_NACHBAR_FREMD("parzelle.bescheid.nachbar-fremd", Art.SATZ),
    BESCHEID_SCHON_IN_GRUPPE("parzelle.bescheid.schon-in-gruppe", Art.SATZ),
    BESCHEID_SCHON_ZUSAMMEN("parzelle.bescheid.schon-zusammen", Art.SATZ),

    /**
     * Die Gruppe waere zu gross - die ZWEITE Zahlquelle.
     *
     * <p>Diese Zahl kommt aus {@code gruppenFelder(spieler)} und nicht aus
     * {@code hoechstzahl(spieler)}. Welche Zahl in welchen Satz geht, sagt
     * {@code Sprachschluessel.zahlquelle}.
     */
    BESCHEID_GRUPPE_ZU_GROSS("parzelle.bescheid.gruppe-zu-gross", Art.GEZAEHLT, "anzahl"),

    BESCHEID_KEINE_GRUPPE("parzelle.bescheid.keine-gruppe", Art.SATZ),
    BESCHEID_AUF_DEM_WEG("parzelle.bescheid.auf-dem-weg", Art.SATZ),
    BESCHEID_HAELT_ZUSAMMEN("parzelle.bescheid.haelt-zusammen", Art.SATZ),

    // --- /parzelle mitbauer ---------------------------------------------------

    MITBAUER_KEINE_PARZELLE("parzelle.mitbauer.keine-parzelle", Art.SATZ),

    /** Wer hier bauen darf - {@code {namen}} ist die Liste oder das Wort darunter. */
    MITBAUER_KOPF("parzelle.mitbauer.kopf", Art.SATZ, "x", "z", "namen"),

    /** Das Wort fuer die leere Liste - als WERT in {@link #MITBAUER_KOPF}. */
    MITBAUER_NIEMAND("parzelle.mitbauer.niemand", Art.WORT),

    /**
     * Die zwei Befehle darunter - nur fuer den Eigentuemer.
     *
     * <p>Die beiden spitzen Klammern um {@code name} sind ZEICHEN und keine Farbe. In der
     * Datei stehen darum zwei Backslashes davor, wie bei
     * {@link #INFO_ZUSAMMENLEGEN_HINWEIS}.
     */
    MITBAUER_BEFEHLE("parzelle.mitbauer.befehle", Art.SATZ),

    MITBAUER_UNBEKANNT("parzelle.mitbauer.unbekannt", Art.SATZ),
    MITBAUER_HINZU_ERTEILT("parzelle.mitbauer.hinzu-erteilt", Art.SATZ, "name"),
    MITBAUER_SCHON_DRIN("parzelle.mitbauer.schon-drin", Art.SATZ, "name"),
    MITBAUER_NIEMAND_MIT_DEM_NAMEN("parzelle.mitbauer.niemand-mit-dem-namen", Art.SATZ),
    MITBAUER_WEG_ERTEILT("parzelle.mitbauer.weg-erteilt", Art.SATZ, "name"),
    MITBAUER_NICHT_DRIN("parzelle.mitbauer.nicht-drin", Art.SATZ, "name"),

    // --- /parzelle aufgeben ---------------------------------------------------

    AUFGEBEN_NICHT_DEINE("parzelle.aufgeben.nicht-deine", Art.SATZ),
    AUFGEBEN_GRUPPE("parzelle.aufgeben.gruppe", Art.SATZ),

    /**
     * Die Warnung vor dem Aufgeben - DREI Chatzeilen in EINEM Satz.
     *
     * <p>Von Hand umgebrochen und darum eine Zeile der Datei mit zwei Umbruchmarken
     * (Bauplan 7). Jede der drei traegt ihre eigene Farbe, und genau deshalb ist es ein
     * Satz und nicht drei: Der Betreiber kann sie umstellen, kuerzen oder zu zweien
     * machen. Drei Schluessel koennte er faerben, aber nicht umstellen.
     */
    AUFGEBEN_WARNUNG("parzelle.aufgeben.warnung", Art.SATZ, "x", "z"),

    AUFGEBEN_ERTEILT("parzelle.aufgeben.erteilt", Art.SATZ, "x", "z"),

    // --- /parzelle zusammenlegen ----------------------------------------------

    /**
     * Die Warnung vor dem Zusammenlegen - FUENF Chatzeilen in EINEM Satz.
     *
     * <p>{@code {nachbar-x}} und {@code {nachbar-z}} kommen schon gerechnet herein: Der
     * Nachbar steht im Satz, und die Richtung bestimmt ihn. Was danach folgt, ist ein
     * eigener Satz - entweder {@link #ZUSAMMENLEGEN_ZU_WENIG} oder
     * {@link #ZUSAMMENLEGEN_BESTAETIGEN}, und welcher, entscheidet der Sendeort.
     */
    ZUSAMMENLEGEN_WARNUNG("parzelle.zusammenlegen.warnung", Art.SATZ, "x", "z",
            "nachbar-x", "nachbar-z", "betrag", "guthaben"),

    /**
     * Das Geld reicht nicht - MIT zwei Leerzeichen Einzug.
     *
     * <p>Derselbe Wortlaut wie {@link #BESCHEID_ZU_WENIG_MARK}, nur eingerueckt, und darum
     * ein eigener Schluessel: Der Einzug steht IM Satz, also sind die beiden nicht
     * dieselbe Zeichenkette. Dieselbe Abwaegung wie bei {@link #INFO_AM_LIMIT}.
     */
    ZUSAMMENLEGEN_ZU_WENIG("parzelle.zusammenlegen.zu-wenig", Art.SATZ),

    /** {@code {richtung}} ist das Befehlswort nord/ost/sued/west aus dem Kern. */
    ZUSAMMENLEGEN_BESTAETIGEN("parzelle.zusammenlegen.bestaetigen", Art.SATZ, "richtung",
            "x", "z"),

    /**
     * Zusammengelegt - die Felderzahl, OHNE Einzahlform.
     *
     * <p>{@link Art#SATZ} und nicht {@link Art#GEZAEHLT}, wie {@link #INFO_GRUPPE}: Heute
     * sagt dieser Satz auch bei einem Feld "Feldern". Eine Einzahlform waere eine
     * inhaltliche Verbesserung und keine Umstellung - sie steht in den offenen Punkten des
     * Bauplans und nicht stillschweigend hier. Darunter steht {@link #BEZAHLT}.
     */
    ZUSAMMENLEGEN_ERTEILT("parzelle.zusammenlegen.erteilt", Art.SATZ, "anzahl"),

    // --- /parzelle auftrennen -------------------------------------------------

    /**
     * Die Warnung vor dem ganzen Auftrennen - DREI Chatzeilen in EINEM Satz.
     *
     * <p>Die dritte traegt zwei doppelte Anfuehrungszeichen mitten im Wert, und jeder Wert
     * der Vorlage steht selbst in doppelten Anfuehrungszeichen. Das ist der eine Satz
     * dieses Zuges, der eine YAML-Maskierung braucht.
     */
    AUFTRENNEN_ALLES_WARNUNG("parzelle.auftrennen.alles.warnung", Art.SATZ, "anzahl"),

    AUFTRENNEN_ALLES_BESTAETIGEN("parzelle.auftrennen.alles.bestaetigen", Art.SATZ, "x",
            "z"),

    /**
     * Aufgetrennt - und genau derselbe Satz, wenn ein Herausloesen bei zwei Feldern die
     * ganze Gruppe trennt.
     *
     * <p>{@code herausgeloestZeilen} ruft dafuer {@code aufgetrenntZeilen} auf und baut
     * die Worte nicht selbst. Ohne Einzahlform, aus demselben Grund wie
     * {@link #ZUSAMMENLEGEN_ERTEILT}.
     */
    AUFTRENNEN_ERTEILT("parzelle.auftrennen.erteilt", Art.SATZ, "anzahl"),

    /**
     * Der Weg kommt zurueck - die Zeile unter BEIDEN Warnungen des Herausloesens.
     *
     * <p>Darum steht sie unter {@code auftrennen} und nicht unter {@code auftrennen.feld}:
     * Sie gehoert zum Einzelfeld UND zum Fall "nur zwei Felder, also ganz auftrennen".
     * Das Inventar fuehrt sie beim Einzelfeld - gemessen steht sie bei beiden.
     */
    AUFTRENNEN_WEG_KOMMT_ZURUECK("parzelle.auftrennen.weg-kommt-zurueck", Art.SATZ),

    /**
     * Zwei Felder lassen sich nicht teilen - DREI Chatzeilen in EINEM Satz.
     *
     * <p>Eigener Schluessel und nicht {@link #AUFTRENNEN_ALLES_WARNUNG}: Dort ist das
     * ganze Auftrennen gewollt, hier ist es die Folge daraus, dass eine Gruppe aus einem
     * Feld es nicht gibt. Die Worte sind darum andere.
     */
    AUFTRENNEN_FELD_WARNUNG_ZWEI("parzelle.auftrennen.feld.warnung-zwei-felder", Art.SATZ,
            "anzahl"),

    /** Die Warnung vor dem Herausloesen - ZWEI Chatzeilen in EINEM Satz. */
    AUFTRENNEN_FELD_WARNUNG("parzelle.auftrennen.feld.warnung", Art.SATZ, "x", "z",
            "anzahl"),

    AUFTRENNEN_FELD_BESTAETIGEN("parzelle.auftrennen.feld.bestaetigen", Art.SATZ, "x", "z"),
    AUFTRENNEN_FELD_ERTEILT("parzelle.auftrennen.feld.erteilt", Art.SATZ, "x", "z",
            "anzahl"),

    // --- Die Abschaetzung der Weltarbeit --------------------------------------

    /**
     * Was von der Flaeche verschwindet - die eine Stelle, an der die 1 fest im Quelltext
     * stand.
     *
     * <p>Jetzt ist es ein {@link Art#GEZAEHLT}, und die 1 steht in der Einzahlzeile der
     * Datei. Die Einzahl DARF den Platzhalter weglassen (Bauplan 4, Teilmenge) - genau
     * damit bleibt "Dabei verschwindet 1 Block" moeglich, und der Betreiber kann trotzdem
     * {@code {anzahl}} hineinschreiben, wenn er will.
     *
     * <p><b>Ob ueberhaupt gesendet wird, entscheidet weiter der Sendeort</b> (Bauplan 5):
     * {@code Art.GEZAEHLT} kennt nur eins und viele. Bei 0 bleibt die Null-Wache ein
     * sichtbares {@code if} in {@code aufwandZeilen} - sonst stuende dem Spieler "Dabei
     * verschwinden 0 Bloecke von der Flaeche." neben "Auf der Wegflaeche steht nichts".
     */
    AUFWAND_VERSCHWINDET("parzelle.aufwand.verschwindet", Art.GEZAEHLT, "anzahl"),

    /** Dasselbe fuer die Fuellung - die zweite Stelle mit einer festen 1. */
    AUFWAND_VERFUELLT("parzelle.aufwand.verfuellt", Art.GEZAEHLT, "anzahl"),

    AUFWAND_NICHTS("parzelle.aufwand.nichts", Art.SATZ),

    /** Die Abschaetzung muss noch warten - der dritte Ternaer ueber dem Wortlaut. */
    ABKLINGZEIT("parzelle.abklingzeit", Art.GEZAEHLT, "anzahl"),

    // --- Die Bodenarbeit ------------------------------------------------------

    /**
     * Was aus der Bodenarbeit geworden ist - {@code {gelungen}} ist eines der zwei Worte
     * darunter.
     *
     * <p>"geaendert" und nicht nur die Zahl: Darin stecken der Weg selbst und das, was
     * geraeumt wurde. {@code {anzahl}} ist eine NACKTE Zahl ohne Tausenderpunkt - so ist
     * es heute im Spiel, und nur Geldbetraege tragen den Punkt.
     */
    BODEN_GELUNGEN("parzelle.boden.gelungen", Art.SATZ, "gelungen", "anzahl"),

    BODEN_WEG_IST_BAULAND("parzelle.boden.weg-ist-bauland", Art.WORT),
    BODEN_WEG_IST_ZURUECK("parzelle.boden.weg-ist-zurueck", Art.WORT),
    BODEN_FEHLER("parzelle.boden.fehler", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    ParzellenSatz(String pfad, Art art, String... platzhalter) {
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
