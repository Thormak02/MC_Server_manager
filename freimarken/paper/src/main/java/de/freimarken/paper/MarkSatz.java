package de.freimarken.paper;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Buchungsgrund;
import java.util.Set;

/**
 * Die Saetze von {@code /mark} - und die beiden Formen, die jeder andere Satz borgt.
 *
 * <p>Die kleinste moegliche erste Gruppe, und sie uebt alle vier Arten auf einmal:
 * {@link Art#SATZ} (die Verlaufszeile mit Farbe und Einzug), {@link Art#WORT} als Wert in
 * einem anderen Satz (die sechs Buchungsgruende, die Waehrung) und {@link Art#MUSTER} (die
 * Zeitform). Eine Pruefmaschine mit null Zeilen laeuft leer durch - das ist der Grund, dass
 * ueberhaupt schon eine Gruppe in Schritt 1 umzieht.
 *
 * <p><b>Die Schluessel der Buchungsgruende kommen aus dem AUFZAEHLUNGSNAMEN und nicht aus
 * {@link Buchungsgrund#schluessel()}.</b> {@code PARZELLE_GEKAUFT} traegt dort einen
 * Unterstrich ({@code parzelle_gekauft}, Buchungsgrund.java:42), und der
 * Datenbankschluessel darf sich aendern, ohne eine Betreiberzeile stumm zu entwerten -
 * {@code Buchungsgrund} verlangt das im eigenen Javadoc ("muss die heutige Wortwahl
 * ueberleben").
 */
enum MarkSatz implements Satz {

    /**
     * Der Stand - die erste der zwei Zeilen von {@code /mark}.
     *
     * <p>Mehrfarbig und darum EINE Zeile der Datei: Der Betrag ist golden, der Rest grau,
     * und das anders gefaerbte Stueck ist ein WERT (Bauplan 7). Wer den Satz umstellt,
     * braucht dafuer keinen zweiten Schluessel.
     *
     * <p>Diese vier Saetze kamen mit Zug 2b von Schritt 2 dazu und nicht mit Schritt 1:
     * Sie entstehen in {@code ParzellenBefehle}, und aus dieser Datei musste am Ende jede
     * Farbe verschwinden. Der Bereich entscheidet sich am Befehl, den der Betreiber
     * TIPPT - er tippt {@code /mark}, also sucht er unter {@code mark.} (Bauplan 3).
     */
    STAND("mark.stand", Art.SATZ, "betrag"),

    /** Die zweite Zeile von {@code /mark}: der Hinweis auf den Verlauf. */
    VERLAUF_HINWEIS("mark.verlauf-hinweis", Art.SATZ),

    /** {@code /mark verlauf} ohne eine einzige Buchung. */
    VERLAUF_LEER("mark.verlauf.leer", Art.SATZ),

    /** Der Kopf ueber den Buchungen. */
    VERLAUF_KOPF("mark.verlauf.kopf", Art.SATZ),

    /**
     * Die eine Zeile von {@code /mark verlauf} - mit Einzug und Farbe IM Satz.
     *
     * <p>Bis zum 04.11.2026 gab {@code Verlaufszeile.text} eine Zeichenkette zurueck, und
     * {@code ParzellenBefehle:225-226} baute daraus {@code Component.text("  " + text,
     * GRAY)}. Beides steht jetzt in diesem Satz: die zwei Leerzeichen am Anfang und das
     * {@code <gray>} um das Ganze. Waere die Zeile eine Zeichenkette geblieben, haette ein
     * {@code <gold>} darin dem Spieler woertlich im Chat gestanden.
     */
    VERLAUF_ZEILE("mark.verlauf.zeile", Art.SATZ, "zeit", "betrag", "grund", "stand"),

    VERLAUF_GRUND_EROEFFNUNG("mark.verlauf.grund.eroeffnung", Art.WORT),
    VERLAUF_GRUND_BESTAND("mark.verlauf.grund.bestand", Art.WORT),
    VERLAUF_GRUND_PARZELLE_GEKAUFT("mark.verlauf.grund.parzelle-gekauft", Art.WORT),
    VERLAUF_GRUND_PARZELLE_ERSTATTET("mark.verlauf.grund.parzelle-erstattet", Art.WORT),
    VERLAUF_GRUND_FREISTADT_GEGRUENDET("mark.verlauf.grund.freistadt-gegruendet",
            Art.WORT),
    VERLAUF_GRUND_FREISTADT_ERSTATTET("mark.verlauf.grund.freistadt-erstattet", Art.WORT),

    /**
     * Die vier Worte des Grundstueckshandels [Teil C3].
     *
     * <p>VIER und nicht eines: Derselbe Betrag ist beim Kaeufer eine Ausgabe und beim
     * Verkaeufer eine Einnahme, und wer seinen Verlauf liest, muss beides
     * auseinanderhalten koennen. Die Gebuehr steht daneben, weil sie eine eigene Zeile
     * ist - und die Erstattung, weil sie der Fall ist, in dem NICHTS geschehen ist.
     */
    VERLAUF_GRUND_GRUNDSTUECK_GEKAUFT("mark.verlauf.grund.grundstueck-gekauft",
            Art.WORT),
    VERLAUF_GRUND_GRUNDSTUECK_VERKAUFT("mark.verlauf.grund.grundstueck-verkauft",
            Art.WORT),
    VERLAUF_GRUND_GRUNDSTUECK_GEBUEHR("mark.verlauf.grund.grundstueck-gebuehr",
            Art.WORT),
    VERLAUF_GRUND_GRUNDSTUECK_ERSTATTET("mark.verlauf.grund.grundstueck-erstattet",
            Art.WORT),
    VERLAUF_GRUND_ZUSAMMENLEGUNG("mark.verlauf.grund.zusammenlegung", Art.WORT),
    VERLAUF_GRUND_ZUSAMMENLEGUNG_ERSTATTET("mark.verlauf.grund.zusammenlegung-erstattet",
            Art.WORT),
    VERLAUF_GRUND_BERUF_VERLASSEN("mark.verlauf.grund.beruf-verlassen", Art.WORT),
    VERLAUF_GRUND_BERUF_ERSTATTET("mark.verlauf.grund.beruf-erstattet", Art.WORT),
    VERLAUF_GRUND_BERUF_VERDIENST("mark.verlauf.grund.beruf-verdienst", Art.WORT),
    VERLAUF_GRUND_BUNDESMARKT_VERKAUF("mark.verlauf.grund.bundesmarkt-verkauf",
            Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.TAGESBONUS} im Verlauf. */
    VERLAUF_GRUND_TAGESBONUS("mark.verlauf.grund.tagesbonus", Art.WORT),

    VERLAUF_GRUND_LADEN_LIZENZ("mark.verlauf.grund.laden-lizenz", Art.WORT),
    VERLAUF_GRUND_LADEN_KAUTION("mark.verlauf.grund.laden-kaution", Art.WORT),
    VERLAUF_GRUND_LADEN_KAUTION_ZURUECK("mark.verlauf.grund.laden-kaution-zurueck",
            Art.WORT),
    VERLAUF_GRUND_LADEN_ERSTATTET("mark.verlauf.grund.laden-erstattet", Art.WORT),

    /**
     * DIE DREI WOERTER DER STADTKASSE [Teil E1].
     *
     * <p>Drei und nicht eines, weil drei verschiedene Bewegungen laufen - und zwei von
     * ihnen sind DIESELBE Bewegung auf zwei Konten: Was der Stadtbesitzer abhebt, ist
     * auf dem Stadtkonto eine Ausgabe und auf seinem eine Einnahme. Mit einem Wort
     * fuer beide stuende in einem der zwei Verlaeufe das Falsche.
     *
     * <p>Dieselbe Bauart wie beim Handel an Kistenshops, wo drei Zeilen fuer einen
     * Handel stehen - und mit derselben Begruendung.
     */
    VERLAUF_GRUND_STADTKASSE_GEBUEHR("mark.verlauf.grund.stadtkasse-gebuehr",
            Art.WORT),
    VERLAUF_GRUND_FREISTADT_AUFGEGEBEN("mark.verlauf.grund.freistadt-aufgegeben",
            Art.WORT),
    VERLAUF_GRUND_FREISTADT_LADENGEBUEHR("mark.verlauf.grund.freistadt-ladengebuehr",
            Art.WORT),
    VERLAUF_GRUND_STADTKASSE_LADENGEBUEHR("mark.verlauf.grund.stadtkasse-ladengebuehr",
            Art.WORT),
    VERLAUF_GRUND_STADTKASSE_ABGEHOBEN("mark.verlauf.grund.stadtkasse-abgehoben",
            Art.WORT),
    VERLAUF_GRUND_STADTKASSE_AUSGEZAHLT("mark.verlauf.grund.stadtkasse-ausgezahlt",
            Art.WORT),

    VERLAUF_GRUND_STADTKASSE_ZURUECK("mark.verlauf.grund.stadtkasse-zurueck",
            Art.WORT),

    /**
     * Das Wort fuer {@code Buchungsgrund.LADEN_MIETE} [Teil C].
     *
     * <p>Der einzige WIEDERKEHRENDE Grund dieses Bereichs: Er steht einmal je Tag und
     * Laden im Verlauf, und ein Mieter sieht ihn oefter als jeden anderen. Darum ist das
     * Wort kurz.
     */
    VERLAUF_GRUND_LADEN_MIETE("mark.verlauf.grund.laden-miete", Art.WORT),

    /**
     * Die zwei Worte der Meerinsel [Schritt 10].
     *
     * <p>Dieselbe Bauart wie bei der Parzelle und der Freistadt: ein Wort fuer den Kauf
     * und ein zweites fuer den Fall, in dem NICHTS geschehen ist. Jenes zweite erzaehlt
     * von einem Fehlschlag, und wer es in seinem Verlauf liest, soll nicht glauben, er
     * habe eine Insel gekauft und wieder verkauft.
     */
    VERLAUF_GRUND_WERDER_GEKAUFT("mark.verlauf.grund.werder-gekauft", Art.WORT),
    VERLAUF_GRUND_WERDER_ERSTATTET("mark.verlauf.grund.werder-erstattet", Art.WORT),

    /**
     * Die Worte der Wolkeninsel [Schritt 11].
     *
     * <p>Zwei, wie bei der Meerinsel - und beide auch dann, wenn die Insel GRATIS ist:
     * Eine Buchung ueber 0 Mark wird geschrieben, und sie ist die Spur, wann die Insel
     * genommen wurde. Ein Grund ohne Wort waere eine Zeile im Verlauf, in der der
     * Datenbankschluessel steht.
     */
    VERLAUF_GRUND_WOLKE_GENOMMEN("mark.verlauf.grund.wolke-genommen", Art.WORT),
    VERLAUF_GRUND_WOLKE_ERSTATTET("mark.verlauf.grund.wolke-erstattet", Art.WORT),

    /** Eine Herausforderung auf der Wolkeninsel erfuellt [Schritt 11, Teil D]. */
    VERLAUF_GRUND_WOLKE_AUFGABE("mark.verlauf.grund.wolke-aufgabe", Art.WORT),

    /** Das Biom einer Wolkeninsel gewechselt [Schritt 11, Teil F]. */
    VERLAUF_GRUND_WOLKE_BIOM("mark.verlauf.grund.wolke-biom", Art.WORT),
    VERLAUF_GRUND_WOLKE_BIOM_ERSTATTET("mark.verlauf.grund.wolke-biom-erstattet",
            Art.WORT),

    /**
     * Die Worte des Handels an Kistenshops (Plan 7.4).
     *
     * <p><b>DREI Zeilen fuer EINEN Handel, und das ist die Entscheidung dahinter:</b> Was
     * der Kunde zahlt, was der Verkaeufer bekommt und was der Staat nimmt, sind drei
     * verschiedene Fluesse - aber zwischen ZWEI Konten und einer Senke [berichtigt
     * 08.11.2026]. Hier stand "drei verschiedene Konten", und ein drittes gibt es
     * nicht: Der Staat hat kein Konto, hier so wenig wie bei der Ladenmiete. Die
     * Gebuehrzeile liegt darum auf dem Konto DESSEN, der das Geld bekommt, und zieht
     * es ihm wieder ab - netto bleibt der Erloes.
     *
     * <p>Am Bundshop ist es noch einfacher: Dort ist nur EIN Spielerkonto beteiligt,
     * und die andere Seite ist eine Quelle oder eine Senke ohne Zeile. Ein stiller
     * Abzug vom Erloes waere trotzdem falsch - er waere nur als Differenz zweier
     * Zahlen zu sehen, und ein Betreiber, der wissen will, was seine Senke einbringt,
     * muesste sie aus zwei Gruenden zusammenrechnen.
     *
     * <p><b>WAS gehandelt wurde, steht in keinem dieser Worte.</b> Dieselbe Trennung wie
     * bei {@link #VERLAUF_GRUND_BERUF_VERDIENST} und
     * {@link #VERLAUF_GRUND_BUNDESMARKT_VERKAUF}: Das Geldbuch fuehrt Geld. Was in
     * welcher Kiste lag, ist eine andere Frage und gehoert nicht in eine Zeile, die ein
     * Spieler unter {@code /mark verlauf} liest.
     *
     * <p>{@link #VERLAUF_GRUND_SHOP_GEBUEHR} faellt nur, wenn die GERECHNETE Gebuehr
     * ueber 0 liegt - entschieden an einer Stelle,
     * {@code Shopregel.Handel.kostetGebuehr}. Nicht der Satz ist die Frage: Bei 5
     * Prozent auf 19 Mark ist die Gebuehr abgerundet 0, und eine Zeile ueber 0 Mark
     * waere eine Zeile ohne Geldbewegung. Weil ab Werk alle Saetze auf 0 stehen, faellt
     * sie heute nie; das Wort steht da, damit es nicht fehlt, wenn der erste Satz
     * hochgeht.
     *
     * <p>{@link #VERLAUF_GRUND_SHOP_ERSTATTET} faellt noch seltener: nur, wenn ein
     * Handel NACH dem Zahlen gescheitert ist. Nach einer genauen Platzrechnung ist
     * der Fall nicht erreichbar - und das Wort steht da, weil "nicht erreichbar" kein
     * Zustand ist, auf den man Geld setzt.
     */
    VERLAUF_GRUND_SHOP_GEKAUFT("mark.verlauf.grund.shop-gekauft", Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.SHOP_VERKAUFT} - siehe oben. */
    VERLAUF_GRUND_SHOP_VERKAUFT("mark.verlauf.grund.shop-verkauft", Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.SHOP_GEBUEHR} - siehe oben. */
    VERLAUF_GRUND_SHOP_GEBUEHR("mark.verlauf.grund.shop-gebuehr", Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.SHOP_ERSTATTET} - siehe oben. */
    VERLAUF_GRUND_SHOP_ERSTATTET("mark.verlauf.grund.shop-erstattet", Art.WORT),

    /**
     * Die sechs Worte der Wechselstube (Plan 8.1) [Schritt 12 Teil F].
     *
     * <p><b>Die Worte sagen, WOHER das Geld kam oder WOHIN es ging - nie, welche Waehrung
     * es war.</b> Das ist die Entscheidung dieser Gruppe, und sie hat einen Grund: Dieselben
     * Schluessel tragen die Zeilen in ZWEI Buechern. Im Markbuch steht "in Goldmark
     * gewechselt", im Goldbuch "aus Mark gewechselt" - und welche Einheit der BETRAG
     * daneben hat, sagt die Zeile, nicht das Wort. Stuende die Waehrung im Wort, hiesse
     * dieselbe Zeile im Goldbuch "5 Goldmark - Mark gekauft".
     *
     * <p>Gelesen werden die Goldbuchzeilen seit Teil G von {@code /mark muenzhaus
     * verlauf}; {@code /mark verlauf} zeigt weiter das Markbuch. Die Worte stehen trotzdem schon da, weil {@code Buchungsgrund} zu
     * JEDEM Grund ein Wort verlangt - und weil das Muenzhaus (Teil G) sie braucht.
     */
    VERLAUF_GRUND_WECHSEL_MARK_WEG("mark.verlauf.grund.wechsel-mark-weg", Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.WECHSEL_GOLDMARK_HER} - siehe oben. */
    VERLAUF_GRUND_WECHSEL_GOLDMARK_HER("mark.verlauf.grund.wechsel-goldmark-her",
            Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.WECHSEL_GOLDMARK_WEG} - siehe oben. */
    VERLAUF_GRUND_WECHSEL_GOLDMARK_WEG("mark.verlauf.grund.wechsel-goldmark-weg",
            Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.WECHSEL_MARK_HER} - der Zeuge des Deckels. */
    VERLAUF_GRUND_WECHSEL_MARK_HER("mark.verlauf.grund.wechsel-mark-her", Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.WECHSEL_MARK_ZURUECK} - eine Erstattung. */
    VERLAUF_GRUND_WECHSEL_MARK_ZURUECK("mark.verlauf.grund.wechsel-mark-zurueck",
            Art.WORT),

    /** Das Wort fuer {@code Buchungsgrund.WECHSEL_GOLDMARK_ZURUECK} - eine Erstattung. */
    VERLAUF_GRUND_WECHSEL_GOLDMARK_ZURUECK("mark.verlauf.grund.wechsel-goldmark-zurueck",
            Art.WORT),

    /**
     * Die Tafel der Wechselstube - der Kopf von {@code /mark tauschen}.
     *
     * <p>Plan 2.1 nennt fuer das Muenzhaus "Kontostand, Verlauf, Wechsel Mark - Goldmark,
     * Wechseldeckel sichtbar". Seit Teil G gibt es das Haus, und es zeigt DIESE Zeilen
     * mit: {@code Tauschtext.standUndKurse} liefert sie an beide Tafeln, damit es den
     * Deckel nicht zweimal in Worten gibt.
     */
    TAUSCH_KOPF("mark.tausch.kopf", Art.SATZ),

    /** Der Goldmarkstand - {@code /mark} zeigt die Mark, diese Zeile die Goldmark. */
    TAUSCH_STAND("mark.tausch.stand", Art.SATZ, "betrag"),

    /** Die zwei Kurse in einer Zeile: was eine Goldmark kostet und was sie bringt. */
    TAUSCH_KURSE("mark.tausch.kurse", Art.SATZ, "kauf", "verkauf"),

    /**
     * Was heute noch zurueckgewechselt werden darf - der SICHTBARE Wechseldeckel.
     *
     * <p>Beide Zahlen in einer Zeile, die Goldmark und die Mark: Der Deckel ist in Mark
     * bemessen, gewechselt wird in Goldmark - wer nur eine der zwei Zahlen liest, rechnet
     * die andere falsch.
     */
    TAUSCH_FREI("mark.tausch.frei", Art.SATZ, "anzahl", "betrag"),

    /** Statt der Zeile darueber, wenn der Betreiber den Rueckweg geschlossen hat. */
    TAUSCH_ZU("mark.tausch.zu", Art.SATZ),

    /** Wie man tauscht - die letzte Zeile der Tafel. */
    TAUSCH_HILFE("mark.tausch.hilfe", Art.SATZ),

    /** Gekauft: so viele Goldmark fuer so viele Mark, und was jetzt dasteht. */
    TAUSCH_GEKAUFT("mark.tausch.gekauft", Art.SATZ, "goldmark", "mark", "stand"),

    /** Zurueckgewechselt - die Gegenseite von {@link #TAUSCH_GEKAUFT}. */
    TAUSCH_VERKAUFT("mark.tausch.verkauft", Art.SATZ, "goldmark", "mark", "stand"),

    /** Das Markguthaben reicht nicht - mit dem Preis und dem, was fehlt. */
    TAUSCH_ZU_WENIG_MARK("mark.tausch.zu-wenig-mark", Art.SATZ, "preis", "fehlt"),

    /** So viele Goldmark hat der Spieler nicht. */
    TAUSCH_ZU_WENIG_GOLDMARK("mark.tausch.zu-wenig-goldmark", Art.SATZ, "anzahl",
            "stand"),

    /**
     * Der Deckel laesst heute weniger zu - mit der Zahl, die noch geht.
     *
     * <p><b>Die Zahl ist die ganze Nachricht.</b> Der Wechsel kuerzt nicht, er sagt ab -
     * also muss der Spieler lesen, was stattdessen moeglich ist, sonst probiert er
     * zwanzigmal.
     */
    TAUSCH_DECKEL_ERREICHT("mark.tausch.deckel-erreicht", Art.SATZ, "anzahl", "betrag"),

    /** Heute geht gar nichts mehr - der Deckel ist voll. */
    TAUSCH_DECKEL_VOLL("mark.tausch.deckel-voll", Art.SATZ, "betrag"),

    /** Eine Kasse hat geschwiegen; was abgebucht war, ist zurueck. */
    TAUSCH_GESTOERT("mark.tausch.gestoert", Art.SATZ),

    /**
     * Die Zeitform des Verlaufs - beim LADEN uebersetzt.
     *
     * <p>Beim Laden und nicht beim ersten {@code /mark verlauf}: Ein unbrauchbares Muster
     * ist dann eine Klage mit Zeilennummer und kein Wurf mitten in einem Befehl.
     */
    ZEIT_VERLAUF_MUSTER("zeit.verlauf-muster", Art.MUSTER),

    /**
     * Das Wort fuer einen Geldbetrag - zum Beispiel {@code "{zahl} Mark"}.
     *
     * <p>Nur das WORT ist des Betreibers. Der Tausenderpunkt und {@code Locale.GERMANY}
     * bleiben im Programm: {@code Locale.forLanguageTag} wirft nie, es verwirft eine
     * fehlerhafte Eingabe stumm und liefert {@code ROOT} - eine Klage dazu ist nicht
     * herstellbar, und eine Probe, die nicht rot werden kann, ist keine.
     */
    WAEHRUNG_BETRAG("waehrung.betrag", Art.WORT, "zahl"),

    /**
     * Dasselbe Wort fuer einen Betrag UNTER einer Mark - zum Beispiel {@code "0,40 Mark"}.
     *
     * <p>Ein zweiter Schluessel und nicht derselbe, obwohl das Wort gleich lautet: Der
     * Verdienst wird in PFENNIGEN gerechnet, und der Handbalken zeigt, was die letzten
     * Sekunden gebracht haben - meistens weniger als eine Mark. Mit
     * {@link #WAEHRUNG_BETRAG} stuende dort "0 Mark", und ein Spieler, der arbeitet,
     * laese, dass er nichts verdient.
     *
     * <p>Die Zahlform bleibt im Programm, wie bei jedem Betrag: das Komma und die zwei
     * Stellen dahinter. Nur das WORT ist des Betreibers - wer seine Waehrung umbenennt,
     * muss es an ZWEI Stellen tun, und diese Zeile sagt es ihm.
     */
    WAEHRUNG_PFENNIGE("waehrung.pfennige", Art.WORT, "zahl"),

    /**
     * Was ein Posten aus dem Muenzhaus gekostet hat - im GOLDbuch [Teil G].
     *
     * <p>Waehrungsfrei formuliert wie die sechs Wechselworte darueber: Welche Einheit die
     * Zahl daneben hat, sagt die Zeile und nicht das Wort. Hier steht trotzdem
     * "Muenzhaus" darin - der Posten ist die Auskunft, die der Spieler im Verlauf sucht.
     */
    VERLAUF_GRUND_MUENZHAUS_KAUF("mark.verlauf.grund.muenzhaus-kauf", Art.WORT),

    /** Die Erstattung dazu - sie faellt nur, wenn die Lieferung nicht zustandekam. */
    VERLAUF_GRUND_MUENZHAUS_ERSTATTET("mark.verlauf.grund.muenzhaus-erstattet", Art.WORT),

    /**
     * Geld von Hand - die zwei Worte zu /freimarken geld [20.11.2026].
     *
     * <p>JE Richtung ein Wort, weil es je Richtung einen Buchungsgrund gibt: Im Verlauf
     * soll der Spieler sehen, ob der Betreiber ihm etwas gegeben oder genommen hat. Es
     * ist die einzige Geldquelle, die nicht aus dem Spiel kommt - wer sie nicht erkennt,
     * sucht einen Verkauf, den es nie gab.
     */
    VERLAUF_GRUND_BETREIBER_GUTSCHRIFT("mark.verlauf.grund.betreiber-gutschrift",
            Art.WORT),
    VERLAUF_GRUND_BETREIBER_ABZUG("mark.verlauf.grund.betreiber-abzug", Art.WORT),

    /**
     * Die drei Worte der Lotterie - Los, Markgewinn, Goldgewinn (Plan 14).
     *
     * <p>Das LOS steht auch mit 0 Mark im Buch, weil das erste am Tag gratis ist. Im
     * Verlauf ist das eine Zeile ohne Betrag, und das Wort ist dort die ganze Auskunft:
     * Ohne es saehe der Spieler eine 0 und wuesste nicht, wofuer.
     *
     * <p>Der GOLDgewinn steht hier und nicht in einer eigenen Aufzaehlung, obwohl er im
     * Goldbuch landet - genau wie {@link #VERLAUF_GRUND_MUENZHAUS_KAUF}. Welche Einheit
     * die Zahl daneben hat, sagt die Zeile und nicht das Wort.
     */
    VERLAUF_GRUND_LOS_GEZOGEN("mark.verlauf.grund.los-gezogen", Art.WORT),
    VERLAUF_GRUND_LOS_ERSTATTET("mark.verlauf.grund.los-erstattet", Art.WORT),
    VERLAUF_GRUND_LOTTERIE_GEWINN("mark.verlauf.grund.lotterie-gewinn", Art.WORT),
    VERLAUF_GRUND_LOTTERIE_GOLDGEWINN("mark.verlauf.grund.lotterie-goldgewinn", Art.WORT),

    /**
     * Die drei Worte des Angelwettbewerbs - Startgeld, Preis, Erstattung (Plan 14).
     *
     * <p>Der PREIS und das STARTGELD stehen im Verlauf nebeneinander, und die Differenz
     * ihrer Summen ist der Zuschuss des Betreibers. Darum tragen sie verschiedene Worte,
     * auch wenn beide "Wettbewerb" nennen.
     */
    VERLAUF_GRUND_WETTBEWERB_START("mark.verlauf.grund.wettbewerb-start", Art.WORT),
    VERLAUF_GRUND_WETTBEWERB_PREIS("mark.verlauf.grund.wettbewerb-preis", Art.WORT),
    VERLAUF_GRUND_WETTBEWERB_ERSTATTET("mark.verlauf.grund.wettbewerb-erstattet",
            Art.WORT),

    /**
     * Die Tafel des Muenzhauses - der Kopf von {@code /mark muenzhaus} [Teil G].
     *
     * <p>Der ORT, den Plan 2.1 nennt: Kontostand, Verlauf, Wechseldeckel und der Katalog
     * an einer Stelle. Die drei mittleren Zeilen holt sich diese Tafel von der
     * Wechselstube - Stand, Kurse und der sichtbare Deckel stehen in
     * {@link #TAUSCH_STAND}, {@link #TAUSCH_KURSE} und {@link #TAUSCH_FREI}, und eine
     * zweite Fassung derselben Auskunft waere die, die der naechste Umbau vergisst.
     */
    MUENZHAUS_KOPF("mark.muenzhaus.kopf", Art.SATZ),

    /** Der Betreiber hat das Muenzhaus abgeschaltet - und sagt es statt zu schweigen. */
    MUENZHAUS_AUS("mark.muenzhaus.aus", Art.SATZ),

    /**
     * Das Muenzhaus ist an, der Katalog aber leer.
     *
     * <p>Ein eigener Satz und nicht {@link #MUENZHAUS_AUS}: Ein leerer Katalog ist eine
     * andere Aussage als ein geschlossenes Haus - der Kontostand gilt weiter, und morgen
     * kann etwas darin stehen.
     */
    MUENZHAUS_LEER("mark.muenzhaus.leer", Art.SATZ),

    /**
     * EINE Zeile des Katalogs - ein Posten, den dieser Spieler nehmen koennte.
     *
     * <p><b>Eine Zeile je Posten, und alles darin</b>: Name, Preis und Inhalt. Die
     * Gegenprobe steht bei den Herausforderungen, wo {@code Wolkentext.mengen} dasselbe
     * tut - packte man den Inhalt stattdessen in eigene Zeilen, waere die Tafel bei vier
     * Posten zwischen sieben und siebenundzwanzig Zeilen lang, je nachdem, was der
     * Betreiber eingetragen hat.
     *
     * <p>Der INHALT nennt die technischen Namen, wie die Aufgabentafel: Genau diese Namen
     * stehen in der {@code config.yml}, und ein Spieler, der nach einem fragt, bekaeme von
     * seinem Betreiber sonst die falsche Antwort.
     */
    MUENZHAUS_ZEILE("mark.muenzhaus.zeile", Art.SATZ, "name", "preis", "inhalt"),

    /** Dieselbe Zeile fuer einen Posten, dessen Preis er heute nicht aufbringt. */
    MUENZHAUS_ZEILE_ZU_TEUER("mark.muenzhaus.zeile-zu-teuer", Art.SATZ, "name", "preis",
            "inhalt"),

    /**
     * Dieselbe Zeile fuer einen Posten, dessen BEDINGUNG er nicht erfuellt.
     *
     * <p>Ohne die Zahlen, und das ist Absicht: Was genau fehlt, sagt der Versuch
     * ({@link #MUENZHAUS_BEDINGUNG_FEHLT} und je Bedingung eine eigene Zeile). In einer
     * Liste von Posten waere es eine Wand.
     *
     * <p>Gezeigt wird er trotzdem - ein Posten, den der Spieler nicht sieht, ist ein
     * Posten, von dem er nie erfaehrt.
     */
    MUENZHAUS_ZEILE_GESPERRT("mark.muenzhaus.zeile-gesperrt", Art.SATZ, "name", "preis",
            "inhalt"),

    /** Wie man kauft und wo der Verlauf steht - die letzte Zeile der Tafel. */
    MUENZHAUS_HILFE("mark.muenzhaus.hilfe", Art.SATZ),

    /**
     * Die RUECKFRAGE vor einem Kauf - mit Preis, Inhalt und dem Stand danach.
     *
     * <p>Dieselbe Sicherung wie bei {@code /beruf verlassen} und {@code /freistadt
     * gruenden}, und aus demselben Grund: Der Posten wird GETIPPT, er kostet Goldmark, und
     * es gibt keinen Weg zurueck - verkauft wird im Muenzhaus nichts. Wer bei vier
     * aehnlich klingenden Namen den falschen vervollstaendigt, soll nicht schon bezahlt
     * haben.
     */
    MUENZHAUS_FRAGE("mark.muenzhaus.frage", Art.SATZ, "name", "posten", "preis", "inhalt",
            "danach"),

    /** Gekauft: was es war, was es kostete, was jetzt dasteht. */
    MUENZHAUS_GEKAUFT("mark.muenzhaus.gekauft", Art.SATZ, "name", "inhalt", "preis",
            "stand"),

    /** Diesen Posten gibt es nicht - mit dem, was der Spieler getippt hat. */
    MUENZHAUS_UNBEKANNT("mark.muenzhaus.unbekannt", Art.SATZ, "posten"),

    /** Die Kopfzeile der Absage; darunter je fehlende Bedingung eine eigene Zeile. */
    MUENZHAUS_BEDINGUNG_FEHLT("mark.muenzhaus.bedingung-fehlt", Art.SATZ, "name"),

    /** So viel Goldmark hat er nicht - mit Preis, Stand und dem, was fehlt. */
    MUENZHAUS_ZU_WENIG("mark.muenzhaus.zu-wenig", Art.SATZ, "preis", "stand", "fehlt"),

    /**
     * Im Rucksack ist kein Platz - gefragt wird VOR der Zahlung.
     *
     * <p>Die Zahl der Stuecke, die nicht hineinpassen, ist die ganze Nachricht: Das
     * Muenzhaus kuerzt nicht, es sagt ab - also muss der Spieler lesen, wie viel Platz er
     * machen muss.
     */
    MUENZHAUS_KEIN_PLATZ("mark.muenzhaus.kein-platz", Art.GEZAEHLT, "stueck"),

    /**
     * Kasse, Messung oder Rucksack haben geschwiegen - und es ist NICHTS passiert.
     *
     * <p>Dieser Satz gehoert dem BLICK und der Rueckfrage: der Tafel, dem Goldverlauf
     * und einem Kauf, der vor der Zahlung gescheitert ist. Dass er und
     * {@link #MUENZHAUS_ERSTATTET} zwei Saetze sein muessen, ist am 20.11.2026
     * aufgefallen: Mit einem einzigen las ein Spieler "Dein Geld ist zurueck", wenn er
     * nur die Tafel aufgeschlagen hatte - und suchte eine Buchung, die es nicht gibt.
     */
    MUENZHAUS_GESTOERT("mark.muenzhaus.gestoert", Art.SATZ),

    /** Es war abgebucht, und das Geld ist zurueck - der einzige Fall, der das sagen darf. */
    MUENZHAUS_ERSTATTET("mark.muenzhaus.erstattet", Art.SATZ),

    /**
     * Der Kopf des GOLDverlaufs - mit dem Stand in Goldmark.
     *
     * <p>Ein eigener Kopf und eine eigene Leerzeile, aber KEINE eigene Zeilenform: Die
     * Buchungen selbst kommen aus {@link #VERLAUF_ZEILE}, wie die der Stadtkasse. Eine
     * Buchung soll ueberall gleich aussehen; welche Waehrung die Zahlen haben, sagt das
     * Buch, das {@code Verlaufszeile} hereinbekommt.
     */
    MUENZHAUS_VERLAUF_KOPF("mark.muenzhaus.verlauf-kopf", Art.SATZ, "betrag"),

    /** Noch keine Goldmark bewegt - dann steht hier das statt einer leeren Liste. */
    MUENZHAUS_VERLAUF_LEER("mark.muenzhaus.verlauf-leer", Art.SATZ),

    /**
     * Die fuenf Worte der Bedingungen - je Art eine GEZAEHLTE Zeile [Teil G].
     *
     * <p><b>Warum gezaehlt und nicht ein Wort mit Zahl davor:</b> "1 eigene Parzellen"
     * ist falsches Deutsch, und genau diese Zahl steht in der ausgelieferten Datei. Mit
     * {@link Art#GEZAEHLT} waehlt die Sprachdatei zwischen {@code .eins} und
     * {@code .viele}, und der Betreiber kann beide Formen selbst setzen.
     *
     * <p><b>Warum eigene Saetze und nicht {@code Bedingungsart.beschreibung()}:</b> Jene
     * Zeichenkette steht im Quelltext und sagt es selbst - "fuer die Klage und den
     * Startbericht, nicht fuer das Spiel". Ein Spieler liest nur, was in der
     * {@code sprache.yml} steht.
     *
     * <p>Die ZAHL ist die aus der {@code config.yml} und nicht die gemessene Differenz:
     * {@code Rangregel.Fehlend} rechnet in Sekunden, und ungerechnet gezeigt stuende hier
     * "noch 14.400 Stunden". Zurueckgerechnet wird mit
     * {@code Bedingungsart.inDerDatei} - an derselben einen Stelle wie hin.
     *
     * <p>Gewaehlt werden sie ueber {@link #bedingungssatz}, also mechanisch wie die
     * Buchungsgruende - darum stehen sie in {@code Sprachschluessel.familienDeckung()},
     * und eine Probe schickt JEDE Art durch die ausgelieferte Vorlage.
     */
    MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN("mark.muenzhaus.bedingung.taetige-stunden",
            Art.GEZAEHLT, "zahl"),

    /** Siehe {@link #MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN}. */
    MUENZHAUS_BEDINGUNG_ANWESENHEIT_STUNDEN(
            "mark.muenzhaus.bedingung.anwesenheit-stunden", Art.GEZAEHLT, "zahl"),

    /** Siehe {@link #MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN}. */
    MUENZHAUS_BEDINGUNG_PARZELLEN("mark.muenzhaus.bedingung.parzellen", Art.GEZAEHLT,
            "zahl"),

    /** Siehe {@link #MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN}. */
    MUENZHAUS_BEDINGUNG_BERUF("mark.muenzhaus.bedingung.beruf", Art.GEZAEHLT, "zahl"),

    /** Siehe {@link #MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN}. */
    MUENZHAUS_BEDINGUNG_GUTHABEN("mark.muenzhaus.bedingung.guthaben", Art.GEZAEHLT,
            "zahl"),

    /** Siehe {@link #MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN} [21.11.2026]. */
    MUENZHAUS_BEDINGUNG_RUNDGANG("mark.muenzhaus.bedingung.rundgang", Art.GEZAEHLT,
            "zahl"),

    /**
     * Das Wort fuer einen Betrag in GOLDMARK - zum Beispiel {@code "{zahl} Goldmark"}.
     *
     * <p>Ein eigener Schluessel und nicht derselbe wie {@link #WAEHRUNG_BETRAG}, und das
     * ist der sichtbarste Teil der Entscheidung aus Teil E: Es sind zwei Waehrungen, und
     * sie duerfen nirgends dieselbe Form haben. Wer eine Zahl ohne Einheit liest, kann
     * nicht wissen, ob sie fuenf Mark oder fuenf Goldmark meint - und das sind beim Kurs
     * des Plans Faktor 1.000.
     *
     * <p>Die Zahlform bleibt im Programm, wie bei jedem Betrag. Nur das WORT ist des
     * Betreibers; wer seine Premiumwaehrung umbenennt, aendert diese eine Zeile.
     */
    WAEHRUNG_GOLDMARK("waehrung.goldmark", Art.WORT, "zahl");

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    MarkSatz(String pfad, Art art, String... platzhalter) {
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

    /**
     * Der Pfad, unter dem das Wort eines Buchungsgrundes steht - aus seinem Namen gebildet.
     *
     * <p>Klein, Unterstrich zu Bindestrich. Dass daraus wirklich ein Satz dieser
     * Aufzaehlung wird, sichert {@code SendewegTest.grundIstVollstaendig} ueber
     * {@code values()} zu - in BEIDE Richtungen, damit weder ein Grund ohne Wort noch ein
     * Wort ohne Grund durchgeht.
     */
    static String grundpfad(Buchungsgrund grund) {
        return "mark.verlauf.grund."
                + grund.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /**
     * Der Satz, der eine Bedingung in Worte fasst - je Art einer.
     *
     * <p><b>Ein Schalter ohne {@code default}</b>, wie der des Buchungsgrundes nach seinem
     * Buch: Wer der {@link Bedingungsart} einen sechsten Wert hinzufuegt, wird vom
     * Uebersetzer gefragt, was der Spieler dann liest. Eine Karte daneben waere eine
     * stille Vorgabe, und die fehlende Zeile faende man erst im Chat.
     *
     * <p>Die Pfade folgen den Schluesseln der Aufzaehlung; dass sie es wirklich tun,
     * sichert eine Probe zu - sonst stuende in der Vorlage ein Pfad, den niemand findet.
     */
    static MarkSatz bedingungssatz(Bedingungsart art) {
        return switch (art) {
            case TAETIGE_STUNDEN -> MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN;
            case ANWESENHEIT_STUNDEN -> MUENZHAUS_BEDINGUNG_ANWESENHEIT_STUNDEN;
            case PARZELLEN -> MUENZHAUS_BEDINGUNG_PARZELLEN;
            case BERUF -> MUENZHAUS_BEDINGUNG_BERUF;
            case GUTHABEN -> MUENZHAUS_BEDINGUNG_GUTHABEN;
            case RUNDGANG -> MUENZHAUS_BEDINGUNG_RUNDGANG;
        };
    }
}
