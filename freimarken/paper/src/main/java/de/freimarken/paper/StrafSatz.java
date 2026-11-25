package de.freimarken.paper;

import de.freimarken.kern.Strafart;
import java.util.Set;

/**
 * Die Saetze von {@code /strafe} und {@code /bannliste} (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p><b>Dieser Bereich hat DREI Leserschaften, und das ist seine Besonderheit.</b> Alles
 * unter {@code strafe.} liest ein Moderator; alles unter {@code strafe.getroffen.} liest
 * der BESTRAFTE; alles unter {@code bannliste.} liest jeder. Die Saetze stehen trotzdem in
 * EINER Aufzaehlung: Es ist ein Vorgang, und wer den Wortlaut der einen Seite aendert,
 * soll die andere daneben sehen.
 *
 * <p><b>Die Saetze unter {@code getroffen.} sind der Kern des ganzen Teils.</b> Plan
 * 12c.3: <i>"jeweils mit Dauer und Grund, und der Grund wird dem Betroffenen angezeigt.
 * Eine Strafe, die niemand erklaert, erzeugt nur Wiederkehr."</i> Jeder dieser Saetze
 * enthaelt darum {@code grund} - und je Art einen eigenen, weil ein gemeinsamer mit
 * Platzhalter denjenigen, der gerade hinausgeworfen wird, dasselbe lesen liesse wie den,
 * der dauerhaft gesperrt ist.
 *
 * <p><b>Dauerhaft und befristet sind ZWEI Saetze, nicht einer.</b> Wer dauerhaft gesperrt
 * ist, soll das lesen - und nicht ein Datum im Jahr 2126, das ihn fuer einen Augenblick
 * hoffen laesst. Dasselbe bei der Stummschaltung.
 *
 * <p><b>Die DREI Arten haben drei Worte</b>, gewaehlt ueber {@link #artwort} - dieselbe
 * Bauart wie {@code MeldeSatz.standwort} und {@code BlockSatz.tatwort}. Ein
 * {@code switch} ohne {@code default}: Wer {@link Strafart} einen vierten Wert
 * hinzufuegt, wird vom Uebersetzer gefragt, wie er heisst.
 *
 * <p><b>Die Dauerwoerter sind {@link Art#WORT} und NICHT {@link Art#GEZAEHLT}</b>, obwohl
 * eine Minute nicht zwei sind. Gemessen am 25.11.2026 in Teil C: {@code Sprache.wort}
 * verlangt ausdruecklich {@code Art.WORT} und wirft sonst zur Laufzeit; ein gezaehltes
 * Wortstueck gibt es in diesem Projekt nicht. Geloest ist die Mehrzahlfrage ueber den
 * WORTLAUT - eine Abkuerzung beugt sich nicht ("noch {anzahl} Min.").
 */
enum StrafSatz implements Satz {

    // --- /strafe: was der MODERATOR liest ------------------------------------

    /**
     * Die Strafe steht - mit ihrer NUMMER, der Dauer und dem Grund.
     *
     * <p>Die Nummer gehoert in die Zusage: Sie ist das, was er tippt, wenn er sie
     * aufheben will. Ohne sie muesste er erst die Vorgeschichte aufrufen - und das tut
     * niemand, der sich gerade vertippt hat.
     */
    VERHAENGT("strafe.verhaengt", Art.SATZ, "nummer", "spieler", "art", "dauer",
            "grund"),

    /**
     * Die Strafe steht, und sie hat keine Dauer - ein eigener Satz.
     *
     * <p>GEMESSEN im Startlauf am 25.11.2026: {@link #VERHAENGT} setzte fuer einen
     * Rauswurf die Restzeit ein, und die ist null - im Protokoll stand "Rauswurf fuer 0
     * Sek.". Eine Dauer von null Sekunden ist keine Angabe, sondern eine Rechnung, die
     * nach aussen durchgeschlagen ist.
     *
     * <p>Dasselbe gilt fuer die Ansage ({@link #ANSAGE_OHNE_DAUER}). Die Alternative -
     * im Satz "fuer {dauer}" weglassen - waere ein Satz, der fuer die zwei
     * fortwirkenden Arten die wichtigste Angabe verschweigt.
     */
    VERHAENGT_OHNE_DAUER("strafe.verhaengt-ohne-dauer", Art.SATZ, "nummer", "spieler",
            "art", "grund"),

    /** Die Ansage einer Strafe ohne Dauer - darf leer sein, wie {@link #ANSAGE}. */
    ANSAGE_OHNE_DAUER("strafe.ansage-ohne-dauer", Art.SATZ, LEER_ERLAUBT, "spieler",
            "art", "grund"),

    /** Dem Absender fehlt das Recht fuer diese Art. */
    OHNE_RECHT("strafe.ohne-recht", Art.SATZ, "art"),

    /**
     * Die Dauer braucht das Recht fuer dauerhafte Strafen - mit der GRENZE.
     *
     * <p>Die Grenze steht darin, und das ist der Unterschied zwischen einer Absage und
     * einer Auskunft: Wer nur "zu lang" liest, tippt als naechstes dieselbe Zahl. Nach
     * Plan 12c.2 ist das die Grenze zwischen Moderator und Verwalter, also keine
     * Kleinigkeit - der Moderator soll wissen, wo sie liegt und wen er fragen muss.
     */
    OHNE_RECHT_DAUERHAFT("strafe.ohne-recht-dauerhaft", Art.SATZ, "art", "hoechstens"),

    /**
     * Der Betroffene ist selbst im Team.
     *
     * <p>Der Satz muss sagen, WARUM es nicht geht - sonst haelt der Moderator es fuer
     * einen Fehler des Plugins und versucht es noch dreimal. Und er nennt den Weg: Ein
     * Verwalter darf es.
     */
    OHNE_RECHT_TEAM("strafe.ohne-recht-team", Art.SATZ, "spieler"),

    /**
     * Zum AUFHEBEN fehlt das Recht - ein eigener Satz.
     *
     * <p>Nicht {@link #OHNE_RECHT} mit der Art darin: Beim Aufheben fehlt nicht das
     * Recht fuer eine Art, sondern das zum Zuruecknehmen. Ein gemeinsamer Satz muesste
     * eine Art nennen, die es hier nicht gibt - und der Moderator laese "fuer Sperre
     * fehlt dir das Recht", waehrend er gerade eine aufheben wollte.
     */
    OHNE_RECHT_AUFHEBEN("strafe.ohne-recht-aufheben", Art.SATZ),

    /**
     * Eine UNBEGRENZTE Strafe aufzuheben braucht das Recht fuer dauerhafte Strafen.
     *
     * <p>Auch das ein eigener Satz, aus demselben Grund - und er muss den GRUND
     * nennen: Sonst haelt der Moderator es fuer einen Fehler, dass er eine Strafe
     * nicht aufheben darf, die er selbst sieht. Nach Plan 12c.2 ist das Absicht: Wer
     * nicht dauerhaft sperren darf, nimmt auch keinen dauerhaften Bann zurueck.
     *
     * <p>Er gilt fuer BEIDE Faelle - ohne Ende und laenger als die Hoechstdauer
     * [erweitert am 25.11.2026]. Vorher fragte das Aufheben nur nach dem fehlenden
     * Ende, und ein Moderator nahm den Zehn-Jahres-Bann eines Verwalters zurueck.
     */
    OHNE_RECHT_DAUERHAFT_AUFHEBEN("strafe.ohne-recht-dauerhaft-aufheben", Art.SATZ,
            "nummer"),

    /** Niemand straft sich selbst. */
    SELBST("strafe.selbst", Art.SATZ),

    /**
     * Niemand hebt seine EIGENE Strafe auf - ein eigener Satz.
     *
     * <p>Nicht {@link #SELBST}: Dort steht "bestrafen", und hier wollte er aufheben.
     * Der Riegel dahinter war am 25.11.2026 ganz offen - ein stummgeschalteter
     * Moderator mit dem Recht zum Aufheben las im eigenen Verlauf seine Nummer und war
     * frei.
     */
    SELBST_AUFHEBEN("strafe.selbst-aufheben", Art.SATZ),

    /**
     * Die Strafe gegen ein Teammitglied hebt nur auf, wer das Teamrecht hat.
     *
     * <p>Mit der NUMMER und nicht mit dem Namen: Wer aufhebt, hat die Nummer getippt,
     * nicht den Spieler - und der Name des Betroffenen gehoert in dieser Absage nicht
     * noch einmal genannt.
     */
    OHNE_RECHT_TEAM_AUFHEBEN("strafe.ohne-recht-team-aufheben", Art.SATZ, "nummer"),

    /**
     * Der Grund ist zu kurz - mit der Mindestlaenge.
     *
     * <p>Drei eigene Saetze fuer drei Faelle ({@link #GRUND_ZU_LANG},
     * {@link #GRUND_ZEICHEN}) und nicht ein "Grund unbrauchbar": Der Moderator soll
     * wissen, was er aendern muss. Eine Absage, die das nicht sagt, fuehrt zum zweiten
     * Fehlversuch - mitten im Vorfall.
     */
    GRUND_ZU_KURZ("strafe.grund-zu-kurz", Art.GEZAEHLT, "mindestens"),

    /** Der Grund ist zu lang - mit der Hoechstlaenge. */
    GRUND_ZU_LANG("strafe.grund-zu-lang", Art.GEZAEHLT, "hoechstens"),

    /**
     * Im Grund steht ein Zeichen, das nicht hineingehoert.
     *
     * <p>Der Grund steht in der oeffentlichen Bannliste, und ein Abschnittszeichen darin
     * faelschte Farben. Abgewiesen und nicht entfernt: Ein Grund, der heimlich anders
     * aussieht als getippt, ist in einem Protokoll das Letzte, was man will.
     */
    GRUND_ZEICHEN("strafe.grund-zeichen", Art.SATZ),

    /** Den Spieler kennt der Server nicht. */
    UNBEKANNTER_SPIELER("strafe.unbekannter-spieler", Art.SATZ, "name"),

    /**
     * Die Dauer ist kein brauchbares Wort - mit Beispielen.
     *
     * <p>Die BEISPIELE sind der Zweck des Satzes: Eine Absage "unbrauchbare Dauer" sagt
     * nicht, dass die Einheit fehlt. Wer {@code 10} getippt hat, braucht {@code 10m},
     * und er soll es hier lesen und nicht erraten.
     */
    UNBEKANNTE_DAUER("strafe.unbekannte-dauer", Art.SATZ, "wort", "beispiele"),

    /**
     * Gegen den Spieler gilt schon eine Strafe dieser Art - mit ihrer NUMMER.
     *
     * <p>Die Nummer ist der Weg aus der Absage heraus: aufheben, dann neu verhaengen. Ohne
     * sie ist die Absage eine Sackgasse, und der Moderator sucht die Nummer in der
     * Vorgeschichte - oder gibt auf.
     */
    SCHON_GESTRAFT("strafe.schon-gestraft", Art.SATZ, "spieler", "art", "nummer",
            "bis"),

    /** Diese Nummer gibt es nicht. */
    UNBEKANNT("strafe.unbekannt", Art.SATZ, "nummer"),

    /** Ein Rauswurf laesst sich nicht aufheben - er ist vorbei. */
    NICHT_FORTWIRKEND("strafe.nicht-fortwirkend", Art.SATZ, "nummer", "art"),

    /** Zurueckgenommen. */
    AUFGEHOBEN("strafe.aufgehoben", Art.SATZ, "nummer", "spieler", "art"),

    /** Sie war schon aufgehoben - ein anderer war schneller. */
    SCHON_AUFGEHOBEN("strafe.schon-aufgehoben", Art.SATZ, "nummer", "wer"),

    /** Eine Ablage hat geworfen - nichts ist passiert. */
    GESTOERT("strafe.gestoert", Art.SATZ),

    /**
     * Der Hinweis auf die Zweige - {@code /strafe} ohne weiteres Wort.
     *
     * <p>Brigadier braucht auf dem Wort selbst ein eigenes {@code executes}, sonst endet
     * {@code /strafe} in seinem englischen "Unknown or incomplete command". Statt einer
     * roten englischen Zeile steht hier, was es gibt - und zwar mitten im Vorfall, wo
     * niemand in der Dokumentation nachsieht.
     */
    HINWEIS("strafe.hinweis", Art.SATZ, "arten"),

    /**
     * Die oeffentliche Ansage einer Strafe - DARF LEER SEIN.
     *
     * <p>Leer erlaubt, weil ein Betreiber sie abschalten koennen soll, ohne die
     * Einstellung zu kennen: Wer den Wortlaut loescht, hat sie aus. Die Einstellung
     * {@code moderation.strafen.ansagen} tut dasselbe und ist der ausdrueckliche Weg;
     * {@code Strafwerte.ungereimtheiten()} meldet beides.
     *
     * <p>Sie nennt den GRUND, und das ist Absicht: Wer zusieht, wie jemand grieft, und
     * nichts geschieht, meldet beim naechsten Mal nicht. Die Ansage ist die Antwort an
     * alle, die zugesehen haben.
     */
    ANSAGE("strafe.ansage", Art.SATZ, LEER_ERLAUBT, "spieler", "art", "dauer", "grund"),

    /** Die oeffentliche Ansage einer Aufhebung - darf auch leer sein. */
    ANSAGE_AUFHEBUNG("strafe.ansage-aufhebung", Art.SATZ, LEER_ERLAUBT, "spieler",
            "art", "grund"),

    // --- was der BESTRAFTE liest ---------------------------------------------

    /**
     * Die Zeile beim Rauswurf - der Grund, mit dem der Spieler hinausgeht.
     *
     * <p><b>Dieser Satz IST der Rauswurf</b>, denn mehr bleibt von ihm nicht: Ein Kick
     * ohne Grund ist ein Verbindungsabbruch, von dem der Spieler annimmt, sein Netz sei
     * ausgefallen. Dann kommt er wieder und tut dasselbe.
     */
    GETROFFEN_RAUSWURF("strafe.getroffen.rauswurf", Art.SATZ, "grund", "wer"),

    /** Die Zeile an der Tuer - befristet, mit dem Zeitpunkt der Freigabe. */
    GETROFFEN_BANN("strafe.getroffen.bann", Art.SATZ, "grund", "bis", "dauer"),

    /**
     * Die Zeile an der Tuer - dauerhaft.
     *
     * <p>Ein eigener Satz, kein Platzhalter: Wer dauerhaft gesperrt ist, soll das lesen
     * und nicht ein Datum im Jahr 2126. Dazu der Hinweis auf den Weg - ein Bann ohne
     * Einspruchsmoeglichkeit erzeugt einen zweiten Kontoinhaber.
     */
    GETROFFEN_BANN_DAUERHAFT("strafe.getroffen.bann-dauerhaft", Art.SATZ, "grund"),

    /** Er hat geschrieben und ist stumm - befristet. */
    GETROFFEN_STUMM("strafe.getroffen.stumm", Art.SATZ, "grund", "dauer"),

    /** Er hat geschrieben und ist dauerhaft stumm. */
    GETROFFEN_STUMM_DAUERHAFT("strafe.getroffen.stumm-dauerhaft", Art.SATZ, "grund"),

    /**
     * Er hat einen Befehl versucht, den die Stummschaltung mitsperrt.
     *
     * <p>Ein eigener Satz und nicht {@link #GETROFFEN_STUMM}: Wer {@code /msg} tippt und
     * "du bist stummgeschaltet" liest, haelt es fuer einen Fehler - er hat ja nicht in
     * den Chat geschrieben. Der Satz muss sagen, dass der BEFEHL mitgesperrt ist.
     */
    GETROFFEN_BEFEHL("strafe.getroffen.befehl", Art.SATZ, "befehl", "grund", "dauer"),

    // --- /strafe verlauf: die Vorgeschichte ----------------------------------

    /** Der Kopf der Vorgeschichte. */
    VERLAUF_KOPF("strafe.verlauf.kopf", Art.GEZAEHLT, "anzahl", "spieler"),

    /**
     * Eine Zeile der Vorgeschichte.
     *
     * <p>Mit dem STAND, und das ist der Teil, der die Liste lesbar macht: Eine
     * aufgehobene Strafe steht hier wie jede andere, und ohne das Wort davor saehe ein
     * Moderator drei Banne, von denen zwei zurueckgenommen wurden.
     */
    VERLAUF_ZEILE("strafe.verlauf.zeile", Art.SATZ, "nummer", "art", "stand", "wann",
            "grund"),

    /** Niemand hat etwas gegen diesen Spieler. */
    VERLAUF_LEER("strafe.verlauf.leer", Art.SATZ, "spieler"),

    /** Der Hinweis unter der Liste, wie man eine aufhebt. */
    VERLAUF_HINWEIS("strafe.verlauf.hinweis", Art.SATZ),

    // --- /bannliste: was JEDER liest -----------------------------------------

    /** Der Kopf der oeffentlichen Bannliste. */
    BANNLISTE_KOPF("bannliste.kopf", Art.GEZAEHLT, "anzahl"),

    /**
     * Eine Zeile der oeffentlichen Bannliste - mit Grund und Dauer.
     *
     * <p>Plan 12c.4: <i>"Bannliste mit Grund und Dauer - oeffentlich, wie im
     * Vorbild."</i> Beides steht darum darin, und beides ist unbequem: Die Liste zwingt
     * zu Begruendungen, die standhalten. Genau deshalb gehoert sie dazu.
     */
    BANNLISTE_ZEILE("bannliste.zeile", Art.SATZ, "spieler", "bis", "grund"),

    /** Niemand ist gesperrt. */
    BANNLISTE_LEER("bannliste.leer", Art.SATZ),

    /** Die Liste ist nicht oeffentlich, und der Leser hat kein Recht. */
    BANNLISTE_OHNE_RECHT("bannliste.ohne-recht", Art.SATZ),

    /** Die Liste ist nicht lesbar - die Datenbank antwortet nicht. */
    BANNLISTE_GESTOERT("bannliste.gestoert", Art.SATZ),

    // --- Woerter -------------------------------------------------------------

    /** Das Wort fuer eine Stummschaltung. */
    ART_STUMM("strafe.art.stumm", Art.WORT),

    /** Das Wort fuer einen Rauswurf. */
    ART_RAUSWURF("strafe.art.rauswurf", Art.WORT),

    /** Das Wort fuer einen Bann. */
    ART_BANN("strafe.art.bann", Art.WORT),

    /** Eine Strafe, die noch wirkt. */
    STAND_GILT("strafe.stand.gilt", Art.WORT),

    /** Eine Strafe, deren Zeit abgelaufen ist. */
    STAND_ABGELAUFEN("strafe.stand.abgelaufen", Art.WORT),

    /** Eine Strafe, die zurueckgenommen wurde. */
    STAND_AUFGEHOBEN("strafe.stand.aufgehoben", Art.WORT),

    /** Eine Dauer ohne Ende. */
    DAUER_DAUERHAFT("strafe.dauer.dauerhaft", Art.WORT),

    /**
     * Eine Dauer in Sekunden - {@link Art#WORT} mit {@code anzahl} darin.
     *
     * <p>Siehe den Klassenkopf: Ein eingesetztes Wortstueck ist in diesem Projekt
     * {@code Art.WORT}, und die Mehrzahlfrage loest der WORTLAUT. Die ausgelieferte
     * Vorlage nimmt darum eine Abkuerzung - "Sek." beugt sich nicht. Wer ausschreibt,
     * verantwortet die Beugung selbst.
     */
    DAUER_SEKUNDEN("strafe.dauer.sekunden", Art.WORT, "anzahl"),

    /** Eine Dauer in Minuten. */
    DAUER_MINUTEN("strafe.dauer.minuten", Art.WORT, "anzahl"),

    /** Eine Dauer in Stunden. */
    DAUER_STUNDEN("strafe.dauer.stunden", Art.WORT, "anzahl"),

    /** Eine Dauer in Tagen. */
    DAUER_TAGE("strafe.dauer.tage", Art.WORT, "anzahl"),

    /**
     * Wer an der Konsole steht - fuer eine Strafe ohne Absender.
     *
     * <p>{@code null} heisst in der Ablage die Konsole, und ohne dieses Wort stuende in
     * der Liste eine leere Stelle. Ein erfundener Name waere schlimmer: Er saehe wie ein
     * Spieler aus.
     */
    WER_KONSOLE("strafe.wer.konsole", Art.WORT),

    /**
     * Wie ein Zeitpunkt geschrieben wird.
     *
     * <p>{@link Art#MUSTER}, wie {@code team.zeit-muster} und {@code blockbuch
     * .zeit-muster}: Es wird beim Laden in einen {@code DateTimeFormatter} uebersetzt,
     * und ein unbrauchbares Muster ist eine Klage mit Zeilennummer.
     *
     * <p>Der Zeitpunkt wird in die eingestellte ZONE gerechnet
     * ({@code Zeitwerte.zone}), bevor er hier durchgeht. Ohne das stuende in der
     * Bannliste eine Uhrzeit, die der Spieler nicht wiedererkennt - im Sommer zwei
     * Stunden daneben. Und bei einer Sperre ist das die Zeit, auf die er wartet.
     */
    ZEIT_MUSTER("strafe.zeit-muster", Art.MUSTER);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;
    private final boolean leerErlaubt;

    StrafSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    StrafSatz(String pfad, Art art, boolean leerErlaubt, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.leerErlaubt = leerErlaubt;
        this.platzhalter = Set.of(platzhalter);
    }

    /**
     * Das Wort fuer diese Art - die EINE Stelle, an der die Zuordnung steht.
     *
     * <p>Ein Schalter ohne {@code default}: Wer {@link Strafart} einen vierten Wert
     * hinzufuegt, wird vom Uebersetzer gefragt, wie er heisst. Eine Vorgabe daneben waere
     * eine Art, die im Spiel ohne Wort dasteht - und zwar in der Zeile, die der Bestrafte
     * liest.
     */
    static StrafSatz artwort(Strafart art) {
        return switch (art) {
            case STUMM -> ART_STUMM;
            case RAUSWURF -> ART_RAUSWURF;
            case BANN -> ART_BANN;
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
     * Nur die zwei Ansagen duerfen leer sein.
     *
     * <p>Jeder andere Satz ist eine Antwort auf eine Tat - ein leerer waere ein Befehl,
     * der nichts sagt. Besonders die Saetze unter {@code getroffen.} duerfen es nicht:
     * Ein leerer waere eine Strafe ohne Begruendung, und genau die erzeugt nach Plan
     * 12c.3 nur Wiederkehr.
     */
    @Override
    public boolean darfLeerSein() {
        return leerErlaubt;
    }
}
