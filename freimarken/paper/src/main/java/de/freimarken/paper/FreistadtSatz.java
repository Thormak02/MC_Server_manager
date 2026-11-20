package de.freimarken.paper;

import de.freimarken.kern.Tat;
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
     * Die Zeile aus der {@code config.yml} unter {@link #GRENZE_ERREICHT}.
     *
     * <p>Nur die FARBE ist hier des Betreibers, der Wortlaut steht in
     * {@code hinweise.rang-werbung}. Gebaut wird sie in {@link Rangwerbung}. Unter
     * {@link #KEIN_FREIES_FELD} steht sie NICHT: Dort ist kein Rang der Grund, und ein
     * "mehr erlaubt ein hoeherer Rang" waere dann eine falsche Auskunft.
     */
    RANG_WERBUNG("freistadt.rang-werbung", Art.SATZ, "werbung"),

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
    GRUNDSTUECK_GESTOERT("freistadt.grundstueck.gestoert", Art.SATZ),

    // --- Angebot und Verkauf [Teil C3, 12.11.2026] ------------------------------

    /** Zum Verkauf gestellt - mit dem Preis und dem Weg, wie ein anderer kauft. */
    ANGEBOTEN("freistadt.grundstueck.angeboten", Art.SATZ, "zuschnitt", "preis"),

    /** Zu VERSCHENKEN - ein eigener Satz, denn "fuer 0 Mark" liest sich falsch. */
    ANGEBOTEN_GRATIS("freistadt.grundstueck.angeboten-gratis", Art.SATZ, "zuschnitt"),

    /** Das Angebot ist zurueckgezogen. */
    ZURUECKGEZOGEN("freistadt.grundstueck.zurueckgezogen", Art.SATZ, "zuschnitt"),

    /** GEKAUFT - was der Kaeufer liest. */
    GEKAUFT("freistadt.grundstueck.gekauft", Art.SATZ, "zuschnitt", "preis",
            "verkaeufer"),

    /**
     * Gekauft, aber zu VERSCHENKEN - ein eigener Satz.
     *
     * <p>Wie bei {@link #ANGEBOTEN_GRATIS}: "fuer 0 Mark gekauft" liest sich wie ein
     * Fehler. Ein Geschenk ist etwas anderes als ein Kauf zum Preis null.
     */
    GEKAUFT_GRATIS("freistadt.grundstueck.gekauft-gratis", Art.SATZ, "zuschnitt",
            "verkaeufer"),

    /**
     * VERKAUFT - was der Verkaeufer liest, wenn er online ist.
     *
     * <p>Die erste Meldung dieses Bereichs an einen ZWEITEN Spieler. Ohne sie erfuehre
     * ein Verkaeufer nur aus {@code /mark verlauf}, dass sein Land weg ist - und das
     * sieht er erst, wenn er nachsieht.
     */
    VERKAUFT("freistadt.grundstueck.verkauft", Art.SATZ, "zuschnitt", "preis",
            "kaeufer"),

    /** Dieselbe Meldung ohne Geld - siehe {@link #GEKAUFT_GRATIS}. */
    VERKAUFT_GRATIS("freistadt.grundstueck.verkauft-gratis", Art.SATZ, "zuschnitt",
            "kaeufer"),

    /** Die Gebuehr, die dem Verkaeufer abgezogen wurde - nur wenn sie ueber 0 liegt. */
    VERKAUFT_GEBUEHR("freistadt.grundstueck.verkauft-gebuehr", Art.SATZ, "gebuehr",
            "prozent"),

    /** Dieses Grundstueck steht nicht zum Verkauf. */
    KEIN_ANGEBOT("freistadt.grundstueck.kein-angebot", Art.SATZ),

    /** Am eigenen Grundstueck wird nicht gekauft. */
    EIGENES_GRUNDSTUECK("freistadt.grundstueck.eigenes", Art.SATZ),

    /** Das Guthaben reicht nicht - der Satz nennt Preis UND Fehlbetrag. */
    GRUNDSTUECK_ZU_WENIG("freistadt.grundstueck.zu-wenig-mark", Art.SATZ, "preis",
            "fehlt"),

    /** Ein negativer Preis ist keiner. */
    PREIS_UNGUELTIG("freistadt.grundstueck.preis-ungueltig", Art.SATZ),

    /** Die Ueberschrift der Angebotsliste. */
    ANGEBOT_KOPF("freistadt.angebot.kopf", Art.SATZ),

    /** Eine Zeile der Angebotsliste - MIT Stadt, denn sie geht ueber alle Staedte. */
    ANGEBOT_ZEILE("freistadt.angebot.zeile", Art.SATZ, "zuschnitt", "preis",
            "besitzer"),

    /** Eine Zeile der Angebotsliste fuer ein Geschenk. */
    ANGEBOT_ZEILE_GRATIS("freistadt.angebot.zeile-gratis", Art.SATZ, "zuschnitt",
            "besitzer"),

    /** Es gibt kein einziges Angebot. */
    ANGEBOT_LEER("freistadt.angebot.leer", Art.SATZ),

    // --- Teil D: die Buergerrechte ---------------------------------------------

    /** Die Ueberschrift der Rechteliste - {@code stadt}. */
    RECHTE_KOPF("freistadt.rechte.kopf", Art.SATZ, "stadt"),

    /** Ein Recht, das die Buerger haben - {@code recht}. */
    RECHT_ERLAUBT("freistadt.rechte.erlaubt", Art.SATZ, "recht"),

    /** Ein Recht, das sie nicht haben - {@code recht}. */
    RECHT_GESPERRT("freistadt.rechte.gesperrt", Art.SATZ, "recht"),

    /** Die Fusszeile mit dem Weg zum Aendern. */
    RECHTE_HINWEIS("freistadt.rechte.hinweis", Art.SATZ),

    /** Ein Recht ist dazugekommen - {@code recht}. */
    RECHT_GEAENDERT("freistadt.rechte.geaendert", Art.SATZ, "recht"),

    /** Ein Recht ist weggenommen - {@code recht}. */
    RECHT_GENOMMEN("freistadt.rechte.genommen", Art.SATZ, "recht"),

    /** Es war schon so eingestellt - {@code recht}. */
    RECHT_SCHON_SO("freistadt.rechte.schon-so", Art.SATZ, "recht"),

    /** Er steht in einer fremden Stadt - {@code stadt}, {@code besitzer}. */
    RECHTE_NICHT_DEINE("freistadt.rechte.nicht-deine", Art.SATZ, "stadt", "besitzer"),

    /** Das Wort ist kein Recht - {@code wort}. */
    RECHT_UNBEKANNT("freistadt.rechte.unbekannt", Art.SATZ, "wort"),

    /**
     * Die drei Rechte als WORT - geholt ueber {@link #rechtewort(Tat)}.
     *
     * <p>Sie werden von keinem Zweig namentlich getippt, sondern ueber die
     * Familienfunktion gewaehlt - dieselbe Bauart wie die Buchungsgruende in
     * {@code MarkSatz.grundpfad}. Gedeckt sind sie darum in
     * {@code Sprachschluessel.familienDeckung}, und dass jedes Wort wirklich durch die
     * ausgelieferte Zeile geht, haelt {@code FreistadtBefehlTest}.
     */
    RECHTEWORT_BAUEN("freistadt.rechte.wort.bauen", Art.WORT),

    /** Siehe {@link #RECHTEWORT_BAUEN}. */
    RECHTEWORT_BENUTZEN("freistadt.rechte.wort.benutzen", Art.WORT),

    /** Siehe {@link #RECHTEWORT_BAUEN}. */
    RECHTEWORT_BEHAELTER("freistadt.rechte.wort.behaelter", Art.WORT),

    // --- Teil D5: die Stadtbuergerliste ----------------------------------------

    /** Die Ueberschrift der Buergerliste - {@code stadt}. */
    STADTBUERGER_KOPF("freistadt.stadtbuerger.kopf", Art.SATZ, "stadt"),

    /**
     * Die Zeile des STADTBESITZERS - {@code besitzer}.
     *
     * <p>Eine eigene Zeile und keine Buergerzeile, und das ist die Festlegung von Teil
     * D: Er ist kein Buerger seiner eigenen Stadt. Sein Recht kommt aus dem Besitz des
     * Feldes, und die Verfallsregel aus Plan 16 zaehlt ihn darum nicht mit.
     */
    STADTBUERGER_BESITZER("freistadt.stadtbuerger.besitzer", Art.SATZ, "besitzer"),

    /**
     * Ein Buerger und die Zahl seiner Grundstuecke - {@code name}, {@code anzahl}.
     *
     * <p>{@code Art.GEZAEHLT}, weil sich der WORTLAUT aendert: ein Grundstueck, zwei
     * GrundstueckE. Dieselbe Entscheidung wie bei {@link #GRUNDSTUECK_ZEILE}.
     */
    STADTBUERGER_ZEILE("freistadt.stadtbuerger.zeile", Art.GEZAEHLT, "name", "anzahl"),

    /** Die Stadt hat noch keine Buerger. */
    STADTBUERGER_LEER("freistadt.stadtbuerger.leer", Art.SATZ),

    // --- Teil E1: die Stadtkasse ----------------------------------------------

    /** Die Ueberschrift der Kasse - {@code stadt}, {@code stand}. */
    KASSE_KOPF("freistadt.kasse.kopf", Art.SATZ, "stadt", "stand"),

    // KEIN eigener Satz fuer eine VERLAUFSZEILE: Die baut Verlaufszeile, und zwar
    // dieselbe wie fuer /mark verlauf. Eine Buchung soll ueberall gleich aussehen -
    // mit einem zweiten Satz dafuer gaebe es zwei Formen derselben Zeile, und die
    // zweite bekaeme die naechste Verbesserung nicht mit.

    /**
     * In der Kasse war noch nie etwas.
     *
     * <p>Diese Zeile MUSS es geben, und zwar aus demselben Grund wie bei den
     * Stadtbuergern: Ab Werk steht {@code verkaufsgebuehr-prozent} auf 0, eine Stadt
     * nimmt also gar nichts ein. Wer nur die Ueberschrift sieht, haelt es fuer einen
     * Fehler - und das ist der Normalfall.
     */
    KASSE_LEER("freistadt.kasse.leer", Art.SATZ),

    /** Abgehoben - {@code betrag}, {@code rest}. */
    KASSE_ABGEHOBEN("freistadt.kasse.abgehoben", Art.SATZ, "betrag", "rest"),

    /**
     * So viel liegt nicht darin - {@code stand}.
     *
     * <p>Die Zahl kommt MIT, damit ein Spieler ausrechnen kann, was fehlt - dieselbe
     * Entscheidung wie bei der Absage eines Grundstueckskaufs.
     */
    KASSE_ZU_WENIG("freistadt.kasse.zu-wenig", Art.SATZ, "stand"),

    /** Die Kasse einer fremden Stadt - {@code stadt}, {@code besitzer}. */
    KASSE_NICHT_DEINE("freistadt.kasse.nicht-deine", Art.SATZ, "stadt", "besitzer"),

    // --- Teil E2: der Regeltext -----------------------------------------------

    /** Die Ueberschrift der Regeln - {@code stadt}. */
    REGELN_KOPF("freistadt.regeln.kopf", Art.SATZ, "stadt"),

    /**
     * Der Regeltext selbst - {@code text}.
     *
     * <p>Eine EIGENE Zeile und nicht in den Kopf hineingesetzt, und das ist hier
     * wichtiger als sonst: Der Text ist SPIELEREINGABE. Weil die Sprache erst parst
     * und dann ersetzt, kommt ein {@code <red>} darin zeichengleich beim Leser an -
     * waere der Text in den Wortlaut gespleisst, waere er wirklich rot, und ein
     * Stadtbesitzer koennte sich eine Serverdurchsage bauen.
     */
    REGELN_ZEILE("freistadt.regeln.zeile", Art.SATZ, "text"),

    /**
     * Diese Stadt hat noch keine Regeln - {@code stadt}.
     *
     * <p>Diese Zeile MUSS es geben: Ab Werk hat jede Stadt keine, und wer nur die
     * Ueberschrift sieht, haelt es fuer einen Fehler. Sie nennt ausserdem den Befehl,
     * denn genau hier sucht ihn ein Stadtbesitzer.
     */
    REGELN_LEER("freistadt.regeln.leer", Art.SATZ, "stadt"),

    /** Die Regeln stehen jetzt. */
    REGELN_GESETZT("freistadt.regeln.gesetzt", Art.SATZ),

    /** Die Regeln sind weg. */
    REGELN_GELOESCHT("freistadt.regeln.geloescht", Art.SATZ),

    /** Genau dieser Text stand schon da. */
    REGELN_SCHON_SO("freistadt.regeln.schon-so", Art.SATZ),

    /** Es gab gar keine Regeln zu loeschen. */
    REGELN_WAR_LEER("freistadt.regeln.war-leer", Art.SATZ),

    /** Der Text ist zu lang - {@code groesstens}, {@code hat}. */
    REGELN_ZU_LANG("freistadt.regeln.zu-lang", Art.SATZ, "groesstens", "hat"),

    /** Die Regeln einer fremden Stadt - {@code stadt}, {@code besitzer}. */
    REGELN_NICHT_DEINE("freistadt.regeln.nicht-deine", Art.SATZ, "stadt",
            "besitzer"),

    // --- Das Ende einer Stadt [Teil E6] ---------------------------------------

    /**
     * Die Rueckfrage beim Aufgeben - {@code stadt}, {@code erstattung}, {@code kasse},
     * {@code zusammen}, {@code danach}.
     *
     * <p><b>Alle drei Zahlen</b>, und nicht nur die Summe: Ein Besitzer soll
     * nachrechnen koennen, woher sie kommt. Mit einer einzigen Zahl haelt er die
     * Stadtkasse fuer vergessen oder die Erstattung fuer hoeher als sie ist.
     *
     * <p>{@code danach} ist der Befehl zum Bestaetigen, wie bei {@code freistadt.frage}:
     * Eine Rueckfrage ohne den Befehl, der sie beantwortet, ist eine Sackgasse.
     */
    AUFGABE_FRAGE("freistadt.aufgabe.frage", Art.SATZ, "stadt", "erstattung", "kasse",
            "zusammen", "danach"),

    /** Dieselbe Rueckfrage fuer das Team - mit {@code besitzer} statt der Anrede. */
    RAEUMUNG_FRAGE("freistadt.aufgabe.raeumung-frage", Art.SATZ, "stadt", "erstattung",
            "kasse", "zusammen", "danach"),

    /**
     * Den getippten Stadtnamen gibt es nicht - {@code name} [Teil E6].
     *
     * <p>Ein eigener Satz und nicht freistadt.grundstueck.keine-stadt: Jener sagt "hier
     * steht keine Stadt" und gilt fuer einen STANDORT. Das Team tippt einen NAMEN, und
     * "hier steht keine" waere darauf keine Antwort - es steht vielleicht meilenweit
     * entfernt.
     */
    AUFGABE_NAME_UNBEKANNT("freistadt.aufgabe.name-unbekannt", Art.SATZ, "name"),

    /** Die Stadt ist weg - {@code stadt}, {@code zusammen}. */
    AUFGABE_BEENDET("freistadt.aufgabe.beendet", Art.SATZ, "stadt", "zusammen"),

    /**
     * In der Stadt gehoert noch jemand anderem ein Grundstueck - {@code stadt}.
     *
     * <p>Der Satz, ohne den dieser Befehl ein Diebstahl waere: Ein Stadtbuerger hat
     * sein Grundstueck BEZAHLT. Er nennt darum auch den Ausweg - zurueckkaufen kann der
     * Stadtbesitzer.
     */
    AUFGABE_NOCH_BUERGER("freistadt.aufgabe.noch-buerger", Art.SATZ, "stadt"),

    /**
     * Die Stadt ist weg, das Geld haengt - {@code stadt}, {@code zusammen}.
     *
     * <p>Ein eigener Satz und nicht die Stoerung: Hier ist etwas Unumkehrbares schon
     * geschehen. Er sagt dem Spieler ausdruecklich, dass er sich an den Betreiber
     * wenden soll - der Betrag und sein Name stehen im Protokoll.
     */
    AUFGABE_GELD_HAENGT("freistadt.aufgabe.geld-haengt", Art.SATZ, "stadt",
            "zusammen"),

    // --- Der Verfall [Teil E5] ------------------------------------------------

    /**
     * Die erste Warnung - {@code stadt}, {@code tage} [Plan 16].
     *
     * <p><b>Zwei Stufen und nicht zwei verschickte Nachrichten</b>: Welche ein Besitzer
     * liest, entscheidet allein der Abstand zum Verfall. Die Begruendung steht bei
     * {@code Verfallsregel} - kurz: Ein Zaehler "schon gewarnt" an der Stadtzeile waere
     * eine zweite Wahrheit, er stuende falsch, sobald eine Nachricht verloren geht, und
     * er machte aus einem Besuch keine Entwarnung.
     *
     * <p>{@code tage} statt eines Datums: Ein Datum muesste in der Zone des Servers
     * gelesen werden, und der Spieler sitzt vielleicht in einer anderen. "Noch 42 Tage"
     * ist in jeder Zone dasselbe.
     */
    VERFALL_WARNUNG_FRUEH("freistadt.verfall.warnung-frueh", Art.GEZAEHLT, "stadt",
            "tage"),

    /**
     * Die zweite, dringendere Warnung - dieselben Werte.
     *
     * <p>Ein EIGENER Satz und nicht derselbe mit anderer Zahl: Wer zwei Wochen vor dem
     * Verfall denselben Ton liest wie zwei Monate davor, liest ihn nicht mehr. Dass es
     * zwei sind, ist der ganze Sinn von zwei Stufen.
     */
    VERFALL_WARNUNG_SPAET("freistadt.verfall.warnung-spaet", Art.GEZAEHLT, "stadt",
            "tage"),

    /**
     * Die Stadt IST verfallen - {@code stadt}.
     *
     * <p>Gelesen beim Beitritt, und zwar so lange, bis der Besitzer sie betritt. Kein
     * Platzhalter fuer Tage: Es gibt keine mehr.
     *
     * <p><b>Sagt ausdruecklich, dass nichts verloren ist</b>: Feld, Name und Guthaben
     * bleiben, und Betreten weckt die Stadt. Ohne diesen Satz hielte ein Besitzer seine
     * 400.000 Mark fuer verbrannt und suchte den Betreiber.
     */
    VERFALL_VERFALLEN("freistadt.verfall.verfallen", Art.SATZ, "stadt"),

    /**
     * Sie ist wieder aufgewacht - {@code stadt}.
     *
     * <p>Gesendet in dem Augenblick, in dem der Besuchsstempel den Besitzer in seiner
     * verfallenen Stadt sieht. Ohne diese Zeile waere die Wiederbelebung unsichtbar: Er
     * steht dort, darf ploetzlich bauen und weiss nicht, warum.
     */
    VERFALL_AUFGEWACHT("freistadt.verfall.aufgewacht", Art.SATZ, "stadt"),

    // --- Die Auflage [Teil E3] ------------------------------------------------

    /**
     * Was einer Stadt zur Auflage fehlt - die drei Worte dazu.
     *
     * <p><b>Hier und nicht bei {@code ReiseSatz}</b>, wo sie bis zum 17.11.2026
     * standen: Sie gehoeren der AUFLAGE und nicht einem ihrer beiden Riegel. Seit E3b
     * setzt sie auch die Ladenabsage an einem Shopschild als {@code was} ein - unter
     * {@code warp.} haette der naechste Leser sie beim falschen Befehl gesucht, und
     * eine zweite Kopie daneben waere die zweite Wahrheit.
     *
     * <p>Gewaehlt werden sie an EINER Stelle, {@code Sprachschluessel.auflagenwort} -
     * ein {@code switch} ohne {@code default}, also bricht ein vierter Grund die
     * Uebersetzung und nicht das Spiel.
     */
    AUFLAGE_OHNE_REGELN("freistadt.auflage.ohne-regeln", Art.WORT),
    AUFLAGE_OHNE_BUERGER("freistadt.auflage.ohne-buerger", Art.WORT),
    AUFLAGE_OHNE_BEIDES("freistadt.auflage.ohne-beides", Art.WORT);

    /**
     * Das Wort zu einem Buergerrecht.
     *
     * <p>Ein {@code switch} OHNE Vorgabezweig, und das ist der Riegel: Ein viertes
     * {@link Tat} bricht die Uebersetzung, und zwar an dieser Stelle mit Zeilennummer.
     * Mit einem Vorgabezweig stuende dort eines Tages das Wort fuer "bauen" an einem
     * Recht, das etwas anderes meint.
     */
    public static FreistadtSatz rechtewort(Tat recht) {
        return switch (recht) {
            case BAUEN -> RECHTEWORT_BAUEN;
            case BENUTZEN -> RECHTEWORT_BENUTZEN;
            case BEHAELTER -> RECHTEWORT_BEHAELTER;
        };
    }

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
