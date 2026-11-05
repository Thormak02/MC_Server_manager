package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /freimarken spielzeit}.
 *
 * <p>Sieben Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig
 * arbeitende Haende nie dieselbe Datei. Die Dateien dieses Schrittes sind
 * {@code Spielzeittext} und {@code SpielzeitBefehle}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {dauer}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die Eigenheit dieses Bereichs ist der AUFBAU.</b> Keine andere Gruppe traegt eine
 * Spaltenausrichtung: {@link #HEUTE} und {@link #GESAMT} halten mit drei und mit zwei
 * Leerzeichen dieselbe Spalte, und seit dem 04.11.2026 steht diese Zahl in der Datei und
 * nicht im Programm. Das ist die Entscheidung des Nutzers - Worte, Farben UND Aufbau
 * gehoeren dem Betreiber, aehnlich einem MOTD-Editor. Der Preis: Zwei Zeilen muessen
 * gemeinsam geaendert werden, und nur eine Bildprobe faengt es, wenn nicht
 * ({@code SpielzeitBefehleTest} 33a und 33i).
 *
 * <p><b>Kein einziges {@link Art#GEZAEHLT}, und das ist gemessen.</b> Keine der elf
 * Verzweigungen dieses Bereichs waehlt zwischen einer Einzahl- und einer Mehrzahlform.
 * {@code {minuten} min} darf darum NICHT in {@code .eins}/{@code .viele} mit "1 Minute"
 * und "2 Minuten" aufgespalten werden - das waere eine erfundene Einzahlform und eine
 * Aenderung am Spielbild, die niemand bestellt hat. Dasselbe fuer Stunden und Sekunden.
 *
 * <p><b>Die Einheiten sind NICHT geteilt.</b> {@code min} steht in vier Saetzen
 * ({@link #DAUER_STUNDEN_MINUTEN}, {@link #DAUER_MINUTEN}, {@link #DAUER_UNTER_EINER_MINUTE},
 * {@link #ZUSTAND_UNTAETIG}) und {@code s} in zweien. Ein geteilter Einheitenschluessel
 * koppelte {@code {minuten} min} an {@code gezaehlt wird bis {grenze} min} - der Betreiber
 * koennte die eine Zeile dann nicht aendern, ohne die andere mitzuaendern. Es sind
 * Einheitenzeichen und keine Wortstuecke, die man umstellen will.
 *
 * <p><b>Keine Marke {@code HALB IN KRAFT:}</b>, und zwar nachgezaehlt: Nach diesem Schritt
 * steht kein Wort dieser Anzeige noch im Programm. Die zwei anderen Stellen mit
 * Zeiteinheiten - {@link ReiseSatz#ABKLINGZEIT} und {@link ReiseSatz#WARTEZEIT} - sagen
 * "Sekunden" und nicht "s", ein anderer Wortlaut und damit kein zweiter Ort desselben
 * Satzes. Seit dem ersten Teil von Schritt 5 stehen auch sie in derselben Datei, nur
 * unter {@code reise} statt unter {@code spielzeit}; vorher waren es Stellen im Programm.
 * Wird daraus spaeter eine gemeinsame Einheit, braucht sie die Marke; heute waere sie eine
 * Warnung vor nichts und ausserdem eine Zeitbombe in {@code VorlageTest} 10e, die den
 * GANZEN Vorlagentext liest.
 */
enum SpielzeitSatz implements Satz {

    // --- Die Ueberschrift -----------------------------------------------------

    /**
     * Die Ueberschrift ueber dem eigenen Stand - die einzige goldene Zeile.
     *
     * <p><b>Ohne {@code {name}}, und das darf sich nicht aendern.</b> Bei
     * {@code /execute as <spieler> run freimarken spielzeit} ist der Executor der Spieler
     * und der SENDER die Konsole: Sie liest "Deine Spielzeit" ueber fremden Zahlen. Eine
     * Umformulierung zu "Deine Spielzeit, {name}" waere dort eine Luege, darum traegt der
     * Vertrag diesen Wert nicht.
     */
    KOPF_SELBST("spielzeit.kopf.selbst", Art.SATZ),

    /** Dieselbe Ueberschrift fuer einen anderen Spieler - mit seinem Namen. */
    KOPF_FREMD("spielzeit.kopf.fremd", Art.SATZ, "name"),

    // --- Die vier Zeilen der Anzeige ------------------------------------------

    /**
     * Die erste Zeile: was heute zusammengekommen ist, der Tag und die Tagesgrenze.
     *
     * <p>Der Tag kommt ueber {@link #TAG_MUSTER} aus derselben Datei, die Zone ist eine
     * technische Kennung ({@code Europe/Berlin}) und kein Wort. Die Spaltenbreite steht
     * im Satz - siehe den Kopf dieser Aufzaehlung und {@link #GESAMT}.
     */
    HEUTE("spielzeit.heute", Art.SATZ, "dauer", "tag", "zone"),

    /** Die zweite Zeile: alles. Haelt mit {@link #HEUTE} dieselbe Spalte. */
    GESAMT("spielzeit.gesamt", Art.SATZ, "dauer"),

    /**
     * Die dritte Zeile, solange gerade NICHT gezaehlt wird.
     *
     * <p>Das Wort {@code min} hinter {@code {grenze}} steht in DIESEM Satz und nicht in
     * {@link #DAUER_MINUTEN}: Der Betreiber soll diese Zeile umstellen koennen, ohne die
     * Dauerformen mitzuaendern.
     *
     * <p>Dieser Satz und {@link #ZUSTAND_ABWESENHEIT} koennen sich NIE begegnen:
     * {@code Spielzeitregel} urteilt nur {@code UNTAETIG}, wenn die Grenze nicht null ist,
     * und der Zusatz haengt genau dann an, wenn sie null ist.
     */
    ZUSTAND_UNTAETIG("spielzeit.zustand.untaetig", Art.SATZ, "dauer", "grenze"),

    /**
     * Dieselbe dritte Zeile, solange gezaehlt wird.
     *
     * <p>Drei Werte, weil die Zeile aus bis zu drei Stuecken besteht und trotzdem EINE
     * Chatzeile ist: {@code {abstand}} ist eines der zwei Blaetter unter
     * {@code zustand.abstand}, {@code {abwesenheit}} der Zusatz darunter.
     *
     * <p>{@code {abwesenheit}} wird mit einem LEEREN Wert eingesetzt, solange die
     * Untaetigkeitsregel an ist. Ein leerer WERT und kein leerer Schluessel -
     * {@link #darfLeerSein} bleibt in diesem Bereich durchweg {@code false}, denn in der
     * DATEI muss ein Wortlaut stehen. Die einzige Ausnahme des Projekts sind die zwei
     * TRENNER der Weltenliste in {@link ReiseSatz} - ein Trenner sendet keine Zeile,
     * sondern laesst zwei Eintraege aneinanderstossen.
     */
    ZUSTAND_GEZAEHLT("spielzeit.zustand.gezaehlt", Art.SATZ,
            "menge", "abstand", "abwesenheit"),

    /** Wie {@link #ZUSTAND_GEZAEHLT} endet, solange die Ablage antwortet - mit dem Takt. */
    ZUSTAND_ABSTAND_TAKT("spielzeit.zustand.abstand.takt", Art.WORT, "takt"),

    /**
     * Wie {@link #ZUSTAND_GEZAEHLT} endet, solange der Takt nach einem Fehlschlag nicht
     * schreibt.
     *
     * <p><b>KEIN Platzhalter, und zwar absichtlich.</b> Wie viele Schlaege ausgesetzt
     * werden, leitet {@code Spielzeitbuch} aus {@code Spielzeitregel.KLEMME_IN_TAKTEN} ab.
     * Eine abgeleitete Zahl in diesem Satz waere beim naechsten Aendern dort still falsch,
     * und keine Probe wuerde rot. Was der Spieler wissen muss, ist ohnehin nicht die Zahl,
     * sondern dass weitergezaehlt und nachgeschrieben wird.
     */
    ZUSTAND_ABSTAND_WARTET("spielzeit.zustand.abstand.wartet", Art.WORT),

    /**
     * Der Zusatz an {@link #ZUSTAND_GEZAEHLT}, solange die Untaetigkeitsregel AUS ist.
     *
     * <p>{@code {pfad}} ist {@code Zeitwerte.PFAD_UNTAETIG}, also ein Ort in der
     * {@code config.yml} und nicht in dieser Datei. Er wird EINGESETZT und steht nicht hart
     * im Satz: Hart geschrieben waere er beim naechsten Umbenennen in {@code Zeitwerte}
     * still falsch, und keine Probe wuerde rot.
     */
    ZUSTAND_ABWESENHEIT("spielzeit.zustand.abwesenheit", Art.WORT, "pfad"),

    /**
     * Die vierte und letzte Zeile: dass vor dem Einbau der Messung nichts gezaehlt wurde.
     *
     * <p>Ohne sie liest ein Spieler mit 200 Stunden eine kleine Zahl und meldet einen
     * Fehler. Sie haengt an der Anzeige und wird nicht einzeln gesendet - sonst pruefte
     * keine Probe, dass sie ankommt.
     */
    HINWEIS("spielzeit.hinweis", Art.SATZ),

    // --- Die drei Absagen -----------------------------------------------------

    /**
     * Statt der vier Zeilen, wenn die Ablage nicht antwortet - GRAU und MIT Einzug.
     *
     * <p>Sie steht an der Stelle der Anzeige und sieht darum aus wie sie. Nicht zu
     * verwechseln mit {@link #NICHT_DA}, der einzigen roten Zeile dieses Befehls: Das
     * Javadoc des alten Sendewegs sagte "die Absage ist grau und nicht mehr rot" und meinte
     * DIESE. Wer die zwei vertauscht, nimmt der anderen ihre Warnfarbe.
     */
    NICHT_LESBAR("spielzeit.nicht-lesbar", Art.SATZ),

    /**
     * Die Absage an die Konsole - OHNE Einzug, denn sie steht nicht unter einer
     * Ueberschrift.
     *
     * <p>Die spitze Klammer um {@code spieler} ist ein ZEICHEN und keine Farbe; in der
     * Vorlage stehen dafuer ZWEI Backslashes. Das Wort darin ist der
     * Brigadier-Argumentname aus {@link SpielzeitBefehle#spielzeit()}: Wer ihn dort
     * umbenennt, laesst diesen Satz still falsch stehen.
     */
    KEINE_KONSOLE("spielzeit.keine-konsole", Art.SATZ),

    /**
     * Die Absage fuer einen Spieler, der nicht da ist - die einzige ROTE Zeile, ohne
     * Einzug.
     *
     * <p>Ein eigener Schluessel und nicht {@code anfrage.frage.nicht-da}: Dort geht es
     * darum, dass eine Anfrage keinen Empfaenger hat, hier darum, dass eine MESSUNG nur
     * fuer Anwesende lesbar ist. Zwei Sachverhalte, zwei Saetze - sie beginnen nur mit
     * denselben vier Woertern.
     */
    NICHT_DA("spielzeit.nicht-da", Art.SATZ, "name"),

    // --- Die fuenf Formen einer Dauer -----------------------------------------

    /**
     * Ab einer Stunde, zum Beispiel {@code "12 h 05 min"}.
     *
     * <p>{@code {minuten}} kommt schon ZWEISTELLIG aufgefuellt: "3 h 5 min" ist von
     * "3 h 50 min" auf einen Blick nicht zu unterscheiden. Die Auffuellung ist eine
     * Rechnung ueber dem Wert und kein Wort - dieselbe Trennung wie bei
     * {@code waehrung.betrag}, wo das Wort "Mark" in der Datei steht und der
     * Tausenderpunkt im Programm.
     */
    DAUER_STUNDEN_MINUTEN("spielzeit.dauer.stunden-minuten", Art.WORT,
            "stunden", "minuten"),

    /** Unter einer Stunde, zum Beispiel {@code "7 min"}. */
    DAUER_MINUTEN("spielzeit.dauer.minuten", Art.WORT, "minuten"),

    /**
     * Unter einer Minute, wo Sekunden nicht gezeigt werden.
     *
     * <p>Die 1 steht fest im Wort und ist KEIN Platzhalter: Es ist eine feststehende
     * Wendung und keine Zahlform. Darum auch kein {@link Art#GEZAEHLT}.
     */
    DAUER_UNTER_EINER_MINUTE("spielzeit.dauer.unter-einer-minute", Art.WORT),

    /**
     * Gar nichts - die Null.
     *
     * <p>Der Pfad heisst {@code nichts} und nicht {@code null}: {@code null} ist fuer YAML
     * ein eigenes Wort, und ein Schluessel mit diesem Namen waere ein Fehler, den der
     * Betreiber nicht sieht.
     */
    DAUER_NICHTS("spielzeit.dauer.nichts", Art.WORT),

    /**
     * Unter einer Minute, wo Sekunden gezeigt werden - bei der laufenden Sitzung und bei
     * der Untaetigkeit.
     *
     * <p>Dort sieht ein Spieler zu, ob die Zahl waechst, und "unter 1 min" waere genau da
     * unbrauchbar.
     */
    DAUER_SEKUNDEN("spielzeit.dauer.sekunden", Art.WORT, "sekunden"),

    /**
     * Der Tag in der Zeile {@link #HEUTE} - beim LADEN uebersetzt.
     *
     * <p>Beim Laden und nicht beim ersten Befehl: Ein unbrauchbares Muster ist dann eine
     * Klage mit Zeilennummer und kein Wurf mitten in der Anzeige. {@code Locale.GERMANY}
     * bleibt im Programm - sonst wechselte ein Monatsname die Sprache.
     *
     * <p>Der Pfad steht unter {@code spielzeit.} und nicht neben {@code zeit.verlauf-muster}:
     * Der Betreiber sucht unter dem Befehl, den er TIPPT. Dass Pfad und deklarierende Datei
     * unabhaengig sind, zeigt {@code MarkSatz} - es fuehrt einen {@code zeit.}- und einen
     * {@code waehrung.}-Pfad.
     */
    TAG_MUSTER("spielzeit.tag-muster", Art.MUSTER);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    SpielzeitSatz(String pfad, Art art, String... platzhalter) {
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
