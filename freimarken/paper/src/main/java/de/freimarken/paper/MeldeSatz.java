package de.freimarken.paper;

import de.freimarken.kern.Meldestand;
import java.util.Set;

/**
 * Die Saetze von {@code /melden} und {@code /meldung} (Plan 12c.3, Schritt 16a Teil C).
 *
 * <p><b>Dieser Bereich hat ZWEI Leserschaften, und das ist seine Besonderheit.</b> Alles
 * unter {@code melden.} liest ein gewoehnlicher Spieler; alles unter {@code meldung.}
 * liest ein Moderator. Die Saetze stehen trotzdem in EINER Aufzaehlung: Es ist ein
 * Vorgang, und wer den Wortlaut der einen Seite aendert, soll die andere daneben sehen -
 * eine Absage an den Melder und die Zeile, die der Moderator darueber liest, muessen
 * zusammenpassen.
 *
 * <p><b>Die ANTWORT an den Melder ist der Kern des ganzen Teils.</b> Plan 12c.3 begruendet
 * das Meldewesen mit ihr: <i>"Der Melder bekommt eine Rueckmeldung - sonst meldet beim
 * zweiten Mal niemand mehr."</i> Darum hat jeder Ausgang seinen eigenen Satz
 * ({@link #ANTWORT_ABGESCHLOSSEN} und {@link #ANTWORT_ABGEWIESEN}) und nicht einen
 * gemeinsamen mit einem Platzhalter: Wer liest "deine Meldung wurde bearbeitet", weiss
 * nicht, ob man ihm geglaubt hat.
 *
 * <p><b>Die vier STAENDE haben vier Worte</b>, gewaehlt ueber {@link #standwort} - dieselbe
 * Bauart wie {@code BlockSatz.tatwort} und {@code BerufSatz.taetigkeitswort}. Eine offene
 * Satzfamilie waere hier falsch: {@link Meldestand} ist eine geschlossene Aufzaehlung im
 * Programm, kein Eintrag in der {@code config.yml}, und der Uebersetzer soll fragen, wenn
 * ein fuenfter Stand dazukommt.
 */
enum MeldeSatz implements Satz {

    // --- /melden: was der MELDER liest ---------------------------------------

    /**
     * Die Meldung ist angelegt - mit ihrer NUMMER.
     *
     * <p>Die Nummer gehoert in die Zusage, auch wenn der Melder sie nie tippen wird: Sie
     * ist das, was er nennen kann, wenn er nachfragt. Ohne sie bleibt ihm nur "ich habe
     * neulich mal jemanden gemeldet".
     */
    ANGELEGT("melden.angelegt", Art.SATZ, "nummer", "spieler"),

    /** Auf diesem Server wird nicht gemeldet. */
    AUS("melden.aus", Art.SATZ),

    /**
     * Der Melder muss noch warten - {@link Art#GEZAEHLT}, denn eine Sekunde ist nicht
     * zwei.
     *
     * <p>Die RESTZEIT steht darin. Eine Absage ohne Zahl laesst ihn raten, und er
     * versucht es sofort wieder - die Bremse waere dann eine, die nur aergert.
     */
    ABKLINGZEIT("melden.abklingzeit", Art.GEZAEHLT, "sekunden"),

    /**
     * Er hat schon so viele unerledigte Meldungen, wie erlaubt sind.
     *
     * <p>Der Satz muss sagen, dass die Meldung VERSCHOBEN und nicht verweigert ist -
     * sie geht, sobald eine der offenen bearbeitet ist. Ohne diesen Zusatz liest er eine
     * Absage und meldet nicht wieder.
     */
    ZU_VIELE_OFFENE("melden.zu-viele-offene", Art.GEZAEHLT, "grenze"),

    /** Niemand meldet sich selbst. */
    SELBST("melden.selbst", Art.SATZ),

    /**
     * Der Grund ist zu kurz - mit der Mindestlaenge.
     *
     * <p>Drei eigene Saetze fuer drei Faelle ({@link #GRUND_ZU_LANG},
     * {@link #GRUND_ZEICHEN}) und nicht ein "Grund unbrauchbar": Der Spieler soll wissen,
     * was er aendern muss. Eine Absage, die das nicht sagt, fuehrt zum zweiten
     * Fehlversuch.
     */
    GRUND_ZU_KURZ("melden.grund-zu-kurz", Art.GEZAEHLT, "mindestens"),

    /** Der Grund ist zu lang - mit der Hoechstlaenge. */
    GRUND_ZU_LANG("melden.grund-zu-lang", Art.GEZAEHLT, "hoechstens"),

    /**
     * Im Grund steht ein Zeichen, das nicht hineingehoert.
     *
     * <p>Gemeint sind Steuerzeichen und das Abschnittszeichen. Der Satz nennt den Grund
     * NICHT im Detail - wer Farben faelschen wollte, braucht keine Anleitung.
     */
    GRUND_ZEICHEN("melden.grund-zeichen", Art.SATZ),

    /** Den Spieler kennt dieser Server nicht. */
    UNBEKANNTER_SPIELER("melden.unbekannter-spieler", Art.SATZ, "name"),

    /**
     * Die Konsole kann nicht melden.
     *
     * <p>Und zwar nicht aus Prinzip, sondern weil eine Meldung einen ORT hat: Der
     * Moderator springt hin. Die Konsole steht nirgends. Gelesen und nicht verborgen,
     * wie bei {@code LotterieSatz.NUR_SPIELER}: Ein Betreiber, der den Befehl nicht
     * findet, sucht an der falschen Stelle.
     */
    NUR_SPIELER("melden.nur-spieler", Art.SATZ),

    /** Eine Ablage hat geworfen - nichts ist passiert. */
    GESTOERT("melden.gestoert", Art.SATZ),

    /**
     * Die Zeile, die ANWESENDE Moderatoren bei einer neuen Meldung lesen.
     *
     * <p>Darf leer bleiben: Auf einem Server mit zehn Moderatoren und vielen Meldungen
     * ist eine Zeile je Meldung eine Belaestigung, und die Liste gibt es ohnehin. Wer sie
     * leer laesst, bekommt sie nicht - genau wie bei den Ansagen des Angelwettbewerbs.
     */
    ANSAGE("melden.ansage", Art.SATZ, LEER_ERLAUBT, "nummer", "melder", "betroffener",
            "grund"),

    // --- Die ANTWORT an den Melder - der Kern dieses Teils --------------------

    /** Die Ueberschrift ueber den Antworten beim Beitritt. */
    ANTWORT_KOPF("melden.antwort.kopf", Art.GEZAEHLT, "anzahl"),

    /**
     * Der Meldung wurde stattgegeben.
     *
     * <p>{@code vermerk} ist das, was der Bearbeiter geschrieben hat. Ist er leer, steht
     * dort {@link #OHNE_VERMERK_WORT} - ein leeres Loch mitten im Satz saehe aus wie ein
     * Fehler.
     */
    ANTWORT_ABGESCHLOSSEN("melden.antwort.abgeschlossen", Art.SATZ, "nummer",
            "betroffener", "vermerk"),

    /**
     * Die Meldung wurde fuer unbegruendet gehalten.
     *
     * <p>Ein EIGENER Satz und nicht {@link #ANTWORT_ABGESCHLOSSEN} mit anderem Vermerk:
     * Wer liest "bearbeitet", weiss nicht, ob man ihm geglaubt hat. Und wer erfaehrt,
     * dass nichts dran war, meldet das naechste Mal genauer - nicht gar nicht.
     */
    ANTWORT_ABGEWIESEN("melden.antwort.abgewiesen", Art.SATZ, "nummer", "betroffener",
            "vermerk"),

    /**
     * Steht anstelle des Vermerks, wenn der Bearbeiter keinen geschrieben hat.
     *
     * <p>{@link Art#WORT} und kein Satz: Es wird in die zwei Antwortzeilen EINGESETZT.
     * Darum traegt es keinen Farbtag - er ueberschriebe die Farbe der Zeile.
     */
    OHNE_VERMERK_WORT("melden.antwort.ohne-vermerk-wort", Art.WORT),

    // --- /meldung: was der MODERATOR liest ------------------------------------

    /** Die Ueberschrift der Liste. */
    LISTE_KOPF("meldung.liste.kopf", Art.GEZAEHLT, "anzahl"),

    /**
     * Eine Zeile der Liste.
     *
     * <p>{@code alter} ist die fertig gesetzte Dauer ("vor 3 Stunden"), {@code stand} das
     * Wort aus {@link #standwort}. Das ALTER und nicht der Zeitpunkt: Was einen Moderator
     * interessiert, ist wie lange eine Meldung liegt - das Datum muesste er erst
     * abziehen.
     */
    LISTE_ZEILE("meldung.liste.zeile", Art.SATZ, "nummer", "melder", "betroffener",
            "grund", "alter", "stand"),

    /** Es liegt nichts an. */
    LISTE_LEER("meldung.liste.leer", Art.SATZ),

    /** Der Hinweis unter der Liste - er nennt die Zweige des Befehls. */
    LISTE_HINWEIS("meldung.liste.hinweis", Art.SATZ),

    /** Die Ueberschrift einer einzelnen Meldung. */
    ZEIGEN_KOPF("meldung.zeigen.kopf", Art.SATZ, "nummer", "stand"),

    /** Wer gemeldet hat, wen, und warum. */
    ZEIGEN_WER("meldung.zeigen.wer", Art.SATZ, "melder", "betroffener", "alter"),

    /** Der Grund, in eigener Zeile - er kann lang sein. */
    ZEIGEN_GRUND("meldung.zeigen.grund", Art.SATZ, "grund"),

    /** Der Tatort - die Zeile, die das Hinspringen vorbereitet. */
    ZEIGEN_ORT("meldung.zeigen.ort", Art.SATZ, "welt", "x", "y", "z", "nummer"),

    /** Wer sie bearbeitet - nur bei IN_ARBEIT und danach. */
    ZEIGEN_BEARBEITER("meldung.zeigen.bearbeiter", Art.SATZ, "bearbeiter"),

    /** Der Vermerk - nur bei erledigten Meldungen. */
    ZEIGEN_VERMERK("meldung.zeigen.vermerk", Art.SATZ, "vermerk"),

    /** Genommen. */
    GENOMMEN("meldung.genommen", Art.SATZ, "nummer"),

    /**
     * Ein anderer war schneller - MIT seinem Namen.
     *
     * <p>Der Name ist der Zweck des Satzes: Ein blosses "zu spaet" liesse den zweiten
     * Moderator ratlos. Mit dem Namen weiss er, wen er fragen kann.
     */
    SCHON_GENOMMEN("meldung.schon-genommen", Art.SATZ, "nummer", "bearbeiter"),

    /** Abgeschlossen. */
    ABGESCHLOSSEN("meldung.abgeschlossen", Art.SATZ, "nummer", "melder"),

    /** Abgewiesen. */
    ABGEWIESEN("meldung.abgewiesen", Art.SATZ, "nummer", "melder"),

    /** Daran ist nichts mehr zu tun. */
    SCHON_ERLEDIGT("meldung.schon-erledigt", Art.SATZ, "nummer", "stand"),

    /** Diese Nummer gibt es nicht. */
    UNBEKANNT("meldung.unbekannt", Art.SATZ, "nummer"),

    /** Der Vermerk ist zu lang - mit der Hoechstlaenge. */
    VERMERK_ZU_LANG("meldung.vermerk-zu-lang", Art.GEZAEHLT, "hoechstens"),

    /** Am Tatort angekommen. */
    HIN_UNTERWEGS("meldung.hin.unterwegs", Art.SATZ, "nummer", "welt"),

    /**
     * Die Welt des Tatorts gibt es nicht mehr.
     *
     * <p>Das kommt vor: Eine Meldung aus Kornmark ueberlebt den Reset der Welt, und eine
     * Meldung aus einer abgeschalteten Welt ueberlebt die Aenderung der
     * {@code config.yml}. Der Satz steht hier, damit der Moderator das liest und nicht
     * einen Absturz.
     */
    HIN_WELT_FEHLT("meldung.hin.welt-fehlt", Art.SATZ, "welt"),

    /**
     * Die Konsole kann es nicht - gilt fuer DREI Zweige.
     *
     * <p>Gesendet von {@code hin}, {@code annehmen} und {@code abschliessen}/
     * {@code abweisen}. EIN Satz, weil die Aussage dieselbe ist; sein Pfad heisst
     * darum {@code meldung.nur-spieler} und nicht {@code meldung.hin.nur-spieler} -
     * gemessen am Startlauf vom 25.11.2026, wo er unter dem hin-Pfad fuer das
     * Annehmen erschien. Ein Betreiber, der ihn dort umschreibt, aenderte
     * unwissentlich die Absage von zwei anderen Zweigen.
     *
     * <p><b>Warum die Konsole nicht nehmen kann:</b> Eine Meldung in Arbeit nennt
     * ihren Bearbeiter, und "in Arbeit bei der Konsole" ist keine Auskunft - niemand
     * kann sie ansprechen. Beim Erledigen gilt dasselbe: {@code Meldung} verlangt
     * einen Bearbeiter, sobald der Stand nicht OFFEN ist.
     */
    NUR_SPIELER_MODERATION("meldung.nur-spieler", Art.SATZ),

    /** Eine Ablage hat geworfen. */
    MODERATION_GESTOERT("meldung.gestoert", Art.SATZ),

    // --- Die vier Staende und die Dauer ---------------------------------------

    /** Das Wort fuer {@link Meldestand#OFFEN}. */
    STAND_OFFEN("meldung.stand.offen", Art.WORT),

    /** Das Wort fuer {@link Meldestand#IN_ARBEIT}. */
    STAND_IN_ARBEIT("meldung.stand.in-arbeit", Art.WORT),

    /** Das Wort fuer {@link Meldestand#ABGESCHLOSSEN}. */
    STAND_ABGESCHLOSSEN("meldung.stand.abgeschlossen", Art.WORT),

    /** Das Wort fuer {@link Meldestand#ABGEWIESEN}. */
    STAND_ABGEWIESEN("meldung.stand.abgewiesen", Art.WORT),

    /**
     * Das Alter in Minuten.
     *
     * <p>Drei Worte fuer drei Groessenordnungen ({@link #ALTER_STUNDEN},
     * {@link #ALTER_TAGE}) und nicht eine Zahl in Sekunden: "vor 93600 Sekunden" rechnet
     * niemand im Kopf. Welches gilt, entscheidet {@code Meldetext} - und das ist eine
     * Stelle und nicht drei.
     *
     * <p><b>{@link Art#WORT} und NICHT {@link Art#GEZAEHLT}</b>, obwohl eine Minute nicht
     * zwei ist - und das ist eine Festlegung des Sprachsystems, keine Nachlaessigkeit:
     * {@code Art.GEZAEHLT} ist in diesem Projekt immer eine GESENDETE Zeile, nie ein
     * eingesetztes Wortstueck ({@code Sprache.wort} verlangt {@code Art.WORT}, und
     * {@code Sprachbuch.wortlaut} wirft fuer einen gezaehlten Satz ohne Anzahl). Das
     * Alter wird aber EINGESETZT - in die Listenzeile und in die Einzelansicht.
     *
     * <p>Die Mehrzahlfrage loest darum die ausgelieferte Vorlage mit einer ABKUERZUNG:
     * "vor {anzahl} Min." beugt sich nicht. Wer das Wort ausschreibt, hat die Beugung
     * selbst zu verantworten - und bei einer Uebersichtsliste ist die kurze Form ohnehin
     * die bessere.
     */
    ALTER_MINUTEN("meldung.alter.minuten", Art.WORT, "anzahl"),

    /** Das Alter in Stunden - {@link Art#WORT} aus demselben Grund. */
    ALTER_STUNDEN("meldung.alter.stunden", Art.WORT, "anzahl"),

    /** Das Alter in Tagen - {@link Art#WORT} aus demselben Grund. */
    ALTER_TAGE("meldung.alter.tage", Art.WORT, "anzahl"),

    /** Das Wort fuer eine Meldung, die soeben entstanden ist. */
    ALTER_SOEBEN("meldung.alter.soeben", Art.WORT);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;
    private final boolean leerErlaubt;

    MeldeSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    MeldeSatz(String pfad, Art art, boolean leerErlaubt, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.leerErlaubt = leerErlaubt;
        this.platzhalter = Set.of(platzhalter);
    }

    /**
     * Das Wort fuer diesen Stand - die EINE Stelle, an der die Zuordnung steht.
     *
     * <p>Ein Schalter ohne {@code default}: Wer {@link Meldestand} einen fuenften Wert
     * hinzufuegt, wird vom Uebersetzer gefragt, wie er heisst. Eine Vorgabe daneben waere
     * ein Stand, der im Spiel ohne Wort dasteht - und zwar nur in der Liste des
     * Moderators, also an der Stelle, die niemand prueft.
     */
    static MeldeSatz standwort(Meldestand stand) {
        return switch (stand) {
            case OFFEN -> STAND_OFFEN;
            case IN_ARBEIT -> STAND_IN_ARBEIT;
            case ABGESCHLOSSEN -> STAND_ABGESCHLOSSEN;
            case ABGEWIESEN -> STAND_ABGEWIESEN;
        };
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
     * Nur die Ansage an die Moderatoren darf leer sein.
     *
     * <p>Jeder andere Satz ist eine Antwort auf eine Tat - ein leerer waere ein Befehl,
     * der nichts sagt. Die Ansage dagegen ist eine Benachrichtigung, und wer zehn
     * Moderatoren hat, will sie vielleicht nicht.
     */
    @Override
    public boolean darfLeerSein() {
        return leerErlaubt;
    }
}
